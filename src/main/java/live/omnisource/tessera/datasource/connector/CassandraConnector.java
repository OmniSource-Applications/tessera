package live.omnisource.tessera.datasource.connector;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import live.omnisource.tessera.datasource.ExternalDataSourceFactory;
import live.omnisource.tessera.datasource.connector.cassandra.CassandraSessionFactory;
import live.omnisource.tessera.exceptions.CassandraSessionTestException;
import live.omnisource.tessera.filestore.crypto.SecureFileStore;
import live.omnisource.tessera.model.dto.ColumnMetadata;
import live.omnisource.tessera.model.dto.ExternalSourceCredentials;
import live.omnisource.tessera.model.dto.RawRecord;
import live.omnisource.tessera.model.dto.SchemaMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Slf4j
@Component
public final class CassandraConnector implements DataSourceConnector {
    private static final Set<String> GEO_HINTS = Set.of("point", "polygon", "linestring");

    private final ExternalDataSourceFactory externalDataSourceFactory;
    private final CassandraSessionFactory sessionFactory;
    private final SecureFileStore secureFileStore;
    private final ObjectMapper objectMapper;

    public CassandraConnector(ExternalDataSourceFactory externalDataSourceFactory, CassandraSessionFactory sessionFactory, SecureFileStore secureFileStore, ObjectMapper objectMapper) {
        this.externalDataSourceFactory = externalDataSourceFactory;
        this.sessionFactory = sessionFactory;
        this.secureFileStore = secureFileStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.CASSANDRA;
    }

    @Override
    public ConnectionInfo testConnection(String sourceKey, ExternalSourceCredentials credentials) {
        try (CqlSession testSession = sessionFactory.sessionFor(credentials)) {
            ResultSet resultSet = testSession.execute("SELECT release_version FROM system.local");
            Row row = resultSet.one();

            externalDataSourceFactory.storeCredentials(sourceKey, credentials);

            return ConnectionInfo.ok("Cassandra " + row.getString("release_version"));
        } catch (CassandraSessionTestException e) {
            log.error("Failed to test Cassandra connection {}", e.getMessage());
            return ConnectionInfo.failed(e.getMessage());
        }
    }

    @Override
    public List<String> listSchemas(String secretRefKey) {
        return sessionFor(secretRefKey).getMetadata()
                .getKeyspaces().values().stream()
                .map(ks -> ks.getName().asInternal())
                .filter(name -> !name.startsWith("system"))
                .sorted()
                .toList();
    }

    @Override
    public List<String> listTables(String secretRefKey, String schema) {
        return sessionFor(secretRefKey).getMetadata()
                .getKeyspace(schema)
                .map(ks -> ks.getTables().values().stream()
                        .map(t -> t.getName().asInternal())
                        .sorted()
                        .toList())
                .orElse(List.of());
    }

    @Override
    public SchemaMetadata introspectTable(String secretRefKey, String schema, String table) {
        var session  = sessionFor(secretRefKey);
        var keyspace = session.getMetadata().getKeyspace(schema)
                .orElseThrow(() -> new IllegalArgumentException("Keyspace not found: " + schema));

        var cqlTable = keyspace.getTable(table)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Table not found: " + schema + "." + table));

        var pkNames = cqlTable.getPrimaryKey().stream()
                .map(col -> col.getName().asInternal())
                .collect(java.util.stream.Collectors.toSet());

        var columns = cqlTable.getColumns().values().stream()
                .map(col -> {
                    String name     = col.getName().asInternal();
                    String cqlType  = col.getType().asCql(true, true);
                    boolean isGeo   = isGeoColumn(name, cqlType);
                    return new ColumnMetadata(
                            name,
                            cqlType,
                            cqlType,
                            true,
                            pkNames.contains(name),
                            isGeo
                    );
                }).toList();

        boolean hasGeo = columns.stream().anyMatch(ColumnMetadata::isGeometry);
        return new SchemaMetadata(schema, table, columns, -1L, hasGeo);
    }

    @Override
    public Stream<RawRecord> streamTable(String secretRefKey, String schema, String table, StreamOptions options) {
        var session = sessionFor(secretRefKey);

        var stmt = SimpleStatement.builder(
                        "SELECT * FROM " + schema + "." + table)
                .setPageSize(options.fetchSize())
                .build();

        var resultSet = session.execute(stmt);
        var defs      = resultSet.getColumnDefinitions();
        int n         = defs.size();

        log.debug("Streaming Cassandra {}.{} pageSize={}", schema, table, options.fetchSize());

        return StreamSupport.stream(resultSet.spliterator(), false)
                .map(row -> {
                    var fields = new LinkedHashMap<String, Object>(n);
                    for (var def : defs) {
                        String colName = def.getName().asInternal();
                        try {
                            fields.put(colName, row.getObject(def.getName()));
                        } catch (Exception e) {
                            // Some codec types may not map cleanly — store as string
                            log.trace("Could not map column {} as object, using string", colName);
                            fields.put(colName, row.getString(def.getName()));
                        }
                    }
                    return new RawRecord(schema, table, fields);
                });
    }

    private CqlSession sessionFor(String secretRefKey) {
        return sessionFactory.sessionFor(secretRefKey);
    }

    private boolean isGeoColumn(String name, String cqlType) {
        String lowerName = name.toLowerCase();
        boolean nameHint = lowerName.contains("geom") ||
                lowerName.contains("location") ||
                lowerName.contains("coordinate") ||
                lowerName.contains("shape") ||
                GEO_HINTS.stream().anyMatch(lowerName::contains);

        // DSE (DataStax Enterprise) has a native PointType / LineStringType / PolygonType
        String lowerType = cqlType.toLowerCase();
        boolean typeHint = lowerType.contains("point") ||
                lowerType.contains("linestring") ||
                lowerType.contains("polygon");

        return nameHint || typeHint;
    }
}
