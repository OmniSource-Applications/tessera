package live.omnisource.tessera.datasource.connector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import live.omnisource.tessera.datasource.ExternalDataSourceFactory;
import live.omnisource.tessera.datasource.ResultSetSpliterator;
import live.omnisource.tessera.datasource.introspection.PostgisIntrospector;
import live.omnisource.tessera.filestore.crypto.SecureFileStore;
import live.omnisource.tessera.layer.dto.IntrospectionResult;
import live.omnisource.tessera.model.dto.ColumnMetadata;
import live.omnisource.tessera.model.dto.ExternalSourceCredentials;
import live.omnisource.tessera.model.dto.RawRecord;
import live.omnisource.tessera.model.dto.SchemaMetadata;
import live.omnisource.tessera.util.SqlIdentifiers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Slf4j
@Component
public final class PostGISConnector implements DataSourceConnector {

    private final ExternalDataSourceFactory externalDataSourceFactory;
    private final SecureFileStore secureFileStore;
    private final ObjectMapper objectMapper;

    private DataSource dataSource(String secretKeyRef) {
        return externalDataSourceFactory.forSecretRef(secretKeyRef);
    }

    public PostGISConnector(ExternalDataSourceFactory externalDataSourceFactory, SecureFileStore secureFileStore, ObjectMapper objectMapper) {
        this.externalDataSourceFactory = externalDataSourceFactory;
        this.secureFileStore = secureFileStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.POSTGIS;
    }

    @Override
    public ConnectionInfo testConnection(String sourceKey, ExternalSourceCredentials credentials) {
        try (var c = DriverManager.getConnection(credentials.url(), credentials.username(), credentials.password());
             var st = c.createStatement();
             var rs = st.executeQuery("SELECT version(), PostGIS_Version()")) {
            rs.next();
            String version = rs.getString(1) + " | PostGIS " + rs.getString(2);

            externalDataSourceFactory.storeCredentials(sourceKey, credentials);

            return ConnectionInfo.ok(version);
        } catch (SQLException e) {
            log.error("Failed to test PostGIS connection: {}", e.getMessage());
            return ConnectionInfo.failed(e.getMessage());
        }
    }

    @Override
    public List<String> listSchemas(String secretRefKey) {
        var tmpl = new NamedParameterJdbcTemplate(dataSource(secretRefKey));
        return tmpl.queryForList(
                "SELECT schema_name FROM information_schema.schemata " +
                        "WHERE schema_name NOT IN ('pg_catalog','information_schema') ORDER BY 1",
                Map.of(), String.class);
    }

    @Override
    public List<String> listTables(String secretRefKey, String schema) {
        var tmpl = new NamedParameterJdbcTemplate(dataSource(secretRefKey));
        return tmpl.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = :s ORDER BY 1",
                Map.of("s", schema), String.class);
    }

    @Override
    public SchemaMetadata introspectTable(String secretRefKey, String schema, String table) {
        var tmpl = new NamedParameterJdbcTemplate(dataSource(secretRefKey));
        var sql = """
            SELECT c.column_name, c.data_type, c.udt_name, c.is_nullable,
                   EXISTS(
                     SELECT 1 FROM information_schema.table_constraints tc
                     JOIN information_schema.key_column_usage kcu
                          ON kcu.constraint_name = tc.constraint_name
                          AND kcu.table_schema = tc.table_schema
                     WHERE tc.constraint_type = 'PRIMARY KEY'
                       AND kcu.column_name = c.column_name
                       AND tc.table_name = :t AND tc.table_schema = :s
                   ) AS is_pk
            FROM information_schema.columns c
            WHERE c.table_schema = :s AND c.table_name = :t
            ORDER BY c.ordinal_position
            """;

        var params = Map.of("s", schema, "t", table);
        var columns = tmpl.query(sql, params, (rs, i) -> new ColumnMetadata(
                rs.getString("column_name"),
                rs.getString("data_type"),
                rs.getString("udt_name"),
                "YES".equals(rs.getString("is_nullable")),
                rs.getBoolean("is_pk"),
                isGeoType(rs.getString("udt_name"))
        ));

        var rowCount = tmpl.queryForObject(
                "SELECT reltuples::bigint FROM pg_class c " +
                        "JOIN pg_namespace n ON n.oid = c.relnamespace " +
                        "WHERE n.nspname = :s AND c.relname = :t",
                params, Long.class);

        boolean hasGeo = columns.stream().anyMatch(ColumnMetadata::isGeometry);
        return new SchemaMetadata(schema, table, columns,
                rowCount != null ? rowCount : -1L, hasGeo);
    }

    @Override
    public Stream<RawRecord> streamTable(String secretRefKey, String schema,
                                         String table, StreamOptions opts) {
        try {
            var conn = dataSource(secretRefKey).getConnection();
            conn.setAutoCommit(false);
            var sql = buildSql(schema, table, opts);
            var st = conn.prepareStatement(sql,
                    ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            st.setFetchSize(opts.fetchSize());
            if (opts.checkpointValue() != null) st.setObject(1, opts.checkpointValue());
            var rs = st.executeQuery();
            var meta = rs.getMetaData();
            int n = meta.getColumnCount();
            var names = new String[n];
            for (int i = 1; i <= n; i++) names[i - 1] = meta.getColumnName(i);

            var spliterator = new ResultSetSpliterator(rs, names, conn, st, schema, table);
            return StreamSupport.stream(spliterator, false)
                    .onClose(spliterator::closeQuietly);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to stream " + schema + "." + table, e);
        }
    }

    @Override
    public IntrospectionResult introspect(String secretRefKey) {
        return PostgisIntrospector.introspect(dataSource(secretRefKey));
    }

    private String buildSql(String schema, String table, StreamOptions opts) {
        String safeSchema = SqlIdentifiers.quoteDouble(schema, "schema");
        String safeTable  = SqlIdentifiers.quoteDouble(table, "table");
        var sb = new StringBuilder("SELECT * FROM ")
                .append(safeSchema).append(".").append(safeTable);
        if (opts.checkpointValue() != null && opts.orderByColumn() != null) {
            String safeCol = SqlIdentifiers.quoteDouble(opts.orderByColumn(), "orderByColumn");
            sb.append(" WHERE ").append(safeCol).append(" > ?");
        }
        if (opts.orderByColumn() != null) {
            String safeCol = SqlIdentifiers.quoteDouble(opts.orderByColumn(), "orderByColumn");
            sb.append(" ORDER BY ").append(safeCol);
        }
        if (opts.maxRows() > 0)
            sb.append(" LIMIT ").append(opts.maxRows());
        return sb.toString();
    }

    private boolean isGeoType(String udt) {
        return udt != null && (udt.startsWith("geometry") || udt.startsWith("geography"));
    }
}

