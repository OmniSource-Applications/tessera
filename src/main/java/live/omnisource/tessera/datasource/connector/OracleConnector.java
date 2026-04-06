package live.omnisource.tessera.datasource.connector;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.sql.DataSource;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import live.omnisource.tessera.datasource.ExternalDataSourceFactory;
import live.omnisource.tessera.datasource.ResultSetSpliterator;
import live.omnisource.tessera.datasource.introspection.OracleIntrospector;
import live.omnisource.tessera.layer.dto.IntrospectionResult;
import live.omnisource.tessera.model.dto.ColumnMetadata;
import live.omnisource.tessera.model.dto.ExternalSourceCredentials;
import live.omnisource.tessera.model.dto.RawRecord;
import live.omnisource.tessera.model.dto.SchemaMetadata;
import live.omnisource.tessera.util.SqlIdentifiers;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public final class OracleConnector implements DataSourceConnector {

  private static final Set<String> GEO_TYPE_NAMES =
      Set.of("SDO_GEOMETRY", "MDSYS.SDO_GEOMETRY", "ST_GEOMETRY");

  private final ExternalDataSourceFactory externalDataSourceFactory;

  public OracleConnector(ExternalDataSourceFactory externalDataSourceFactory) {
    this.externalDataSourceFactory = externalDataSourceFactory;
  }

  private DataSource dataSource(String secretKeyRef) {
    return externalDataSourceFactory.forSecretRef(secretKeyRef);
  }

  @Override
  public SourceType sourceType() {
    return SourceType.ORACLE;
  }

  @Override
  public ConnectionInfo testConnection(String sourceKey, ExternalSourceCredentials credentials) {
    try (var c =
            DriverManager.getConnection(
                credentials.url(), credentials.username(), credentials.password());
        var st = c.createStatement();
        var rs = st.executeQuery("SELECT banner FROM v$version WHERE ROWNUM = 1")) {
      rs.next();
      String version = rs.getString(1);

      externalDataSourceFactory.storeCredentials(sourceKey, credentials);
      return ConnectionInfo.ok(version);
    } catch (SQLException e) {
      log.error("Failed to test Oracle connection: {}", e.getMessage());
      return ConnectionInfo.failed(e.getMessage());
    }
  }

  @Override
  public List<String> listSchemas(String secretRefKey) {
    var tmpl = new NamedParameterJdbcTemplate(dataSource(secretRefKey));
    return tmpl.queryForList(
        "SELECT username FROM all_users "
            + "WHERE username NOT IN ('SYS','SYSTEM','DBSNMP','OUTLN','MDSYS','CTXSYS','XDB','ANONYMOUS') "
            + "ORDER BY username",
        Map.of(),
        String.class);
  }

  @Override
  public List<String> listTables(String secretRefKey, String schema) {
    var tmpl = new NamedParameterJdbcTemplate(dataSource(secretRefKey));
    return tmpl.queryForList(
        "SELECT table_name FROM all_tables WHERE owner = :s ORDER BY table_name",
        Map.of("s", schema.toUpperCase()),
        String.class);
  }

  @Override
  public SchemaMetadata introspectTable(String secretRefKey, String schema, String table) {
    var tmpl = new NamedParameterJdbcTemplate(dataSource(secretRefKey));
    var sql =
        """
            SELECT c.COLUMN_NAME, c.DATA_TYPE,
                   CASE WHEN c.DATA_TYPE_OWNER IS NOT NULL
                        THEN c.DATA_TYPE_OWNER || '.' || c.DATA_TYPE
                        ELSE c.DATA_TYPE END AS full_type,
                   c.NULLABLE,
                   CASE WHEN cc.CONSTRAINT_NAME IS NOT NULL THEN 1 ELSE 0 END AS is_pk
            FROM all_tab_columns c
            LEFT JOIN (
                SELECT col.column_name, con.constraint_name
                FROM all_cons_columns col
                JOIN all_constraints con ON con.constraint_name = col.constraint_name
                  AND con.owner = col.owner
                WHERE con.constraint_type = 'P'
                  AND con.owner = :s AND con.table_name = :t
            ) cc ON cc.column_name = c.column_name
            WHERE c.owner = :s AND c.table_name = :t
            ORDER BY c.column_id
            """;

    var params = Map.of("s", schema.toUpperCase(), "t", table.toUpperCase());
    var columns =
        tmpl.query(
            sql,
            params,
            (rs, i) -> {
              String dataType = rs.getString("DATA_TYPE");
              String fullType = rs.getString("full_type");
              return new ColumnMetadata(
                  rs.getString("COLUMN_NAME"),
                  dataType,
                  fullType,
                  "Y".equals(rs.getString("NULLABLE")),
                  rs.getInt("is_pk") == 1,
                  isGeoType(dataType, fullType));
            });

    var rowCount =
        tmpl.queryForObject(
            "SELECT num_rows FROM all_tables WHERE owner = :s AND table_name = :t",
            params,
            Long.class);

    boolean hasGeo = columns.stream().anyMatch(ColumnMetadata::isGeometry);
    return new SchemaMetadata(schema, table, columns, rowCount != null ? rowCount : -1L, hasGeo);
  }

  @Override
  public Stream<RawRecord> streamTable(
      String secretRefKey, String schema, String table, StreamOptions opts) {
    try {
      var conn = dataSource(secretRefKey).getConnection();
      conn.setAutoCommit(false);
      var sql = buildSql(schema, table, opts);
      var st = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
      st.setFetchSize(opts.fetchSize());
      if (opts.checkpointValue() != null) st.setObject(1, opts.checkpointValue());
      var rs = st.executeQuery();
      var meta = rs.getMetaData();
      int n = meta.getColumnCount();
      var names = new String[n];
      for (int i = 1; i <= n; i++) names[i - 1] = meta.getColumnName(i);

      var spliterator = new ResultSetSpliterator(rs, names, conn, st, schema, table);
      return StreamSupport.stream(spliterator, false).onClose(spliterator::closeQuietly);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to stream " + schema + "." + table, e);
    }
  }

  @Override
  public IntrospectionResult introspect(String secretRefKey) {
    return OracleIntrospector.introspect(dataSource(secretRefKey));
  }

  private String buildSql(String schema, String table, StreamOptions opts) {
    String safeSchema = SqlIdentifiers.quoteDouble(schema, "schema");
    String safeTable = SqlIdentifiers.quoteDouble(table, "table");
    var sb = new StringBuilder("SELECT * FROM ").append(safeSchema).append(".").append(safeTable);
    if (opts.checkpointValue() != null && opts.orderByColumn() != null) {
      String safeCol = SqlIdentifiers.quoteDouble(opts.orderByColumn(), "orderByColumn");
      sb.append(" WHERE ").append(safeCol).append(" > ?");
    }
    if (opts.orderByColumn() != null) {
      String safeCol = SqlIdentifiers.quoteDouble(opts.orderByColumn(), "orderByColumn");
      sb.append(" ORDER BY ").append(safeCol);
    }
    if (opts.maxRows() > 0) sb.append(" FETCH FIRST ").append(opts.maxRows()).append(" ROWS ONLY");
    return sb.toString();
  }

  private boolean isGeoType(String dataType, String fullType) {
    if (dataType == null) return false;
    String upper = dataType.toUpperCase();
    if (GEO_TYPE_NAMES.contains(upper)) return true;
    if (fullType != null && GEO_TYPE_NAMES.contains(fullType.toUpperCase())) return true;
    return false;
  }
}
