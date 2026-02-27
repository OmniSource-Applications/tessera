package live.omnisource.tessera.datasource.connector;

import live.omnisource.tessera.exceptions.DataStoreNotFoundException;
import live.omnisource.tessera.exceptions.DataStoreValidationException;
import live.omnisource.tessera.layer.dto.IntrospectionResult;
import live.omnisource.tessera.model.dto.ExternalSourceCredentials;
import live.omnisource.tessera.model.dto.RawRecord;
import live.omnisource.tessera.model.dto.SchemaMetadata;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

public sealed interface DataSourceConnector
        permits CassandraConnector, PostGISConnector {

    /** The source type this connector handles. */
    SourceType sourceType();

    /** Verify connectivity and return server version info. */
    ConnectionInfo testConnection(String sourceKey, ExternalSourceCredentials credentials);

    /** List available schemas / keyspaces / index patterns. */
    List<String> listSchemas(String secretRefKey);

    /** List tables / collections / indices within a schema. */
    List<String> listTables(String secretRefKey, String schema);

    /** Full column-level metadata for a given table. */

    SchemaMetadata introspectTable(String secretRefKey, String schema, String table);

    /**
     * Stream rows from a table lazily.
     * Caller MUST close the returned Stream to release the underlying connection.
     */
    Stream<RawRecord> streamTable(String secretRefKey, String schema,
                                  String table, StreamOptions options);

    IntrospectionResult introspect(String secretRefKey);

    // ── Types ─────────────────────────────────────────────────────────────

    enum SourceType { CASSANDRA, POSTGIS }

    record ConnectionInfo(String version, boolean connected, String errorMessage) {
        public static ConnectionInfo ok(String version) {
            return new ConnectionInfo(version, true, null);
        }
        public static ConnectionInfo failed(String error) {
            return new ConnectionInfo(null, false, error);
        }
    }

    record StreamOptions(
            String orderByColumn,
            Object checkpointValue,
            int fetchSize,
            int maxRows
    ) {
        public static StreamOptions full() {
            return new StreamOptions(null, null, 5000, 0);
        }
        public static StreamOptions since(String orderByColumn, Object checkpoint) {
            return new StreamOptions(orderByColumn, checkpoint, 5000, 0);
        }
    }
}
