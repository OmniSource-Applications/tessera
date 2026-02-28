package live.omnisource.tessera.layer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Persisted metadata for a single layer within a data store.
 * Written to {workspace}/data/{datastore}/layers/{layer}/layer.json
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LayerRecord(
        LayerDto layer,
        String sourceSchema,       // e.g. "public"
        String sourceTable,        // e.g. "points_of_interest"
        String geometryColumn,     // e.g. "geom"
        String geometryType,       // e.g. "Point", "Polygon", "LineString"
        int srid,                  // e.g. 4326
        long rowCount,
        double[] extent,           // [minX, minY, maxX, maxY]
        String status,             // ACTIVE, DISABLED
        SyncConfig syncConfig      // nullable — null means manual-only
) {

    /**
     * Configuration for automated polling sync.
     *
     * @param enabled             whether the scheduler should poll this layer
     * @param pollIntervalSeconds how often to check for new data (minimum 30)
     * @param orderByColumn       column to ORDER BY for incremental reads (e.g. "updated_at", "id").
     *                            null → full rescan with hash-based dedup each poll.
     */
    public record SyncConfig(
            boolean enabled,
            int pollIntervalSeconds,
            String orderByColumn
    ) {
        public static SyncConfig disabled() {
            return new SyncConfig(false, 300, null);
        }

        public static SyncConfig defaults() {
            return new SyncConfig(true, 300, null);
        }
    }

    public static LayerRecord fromIntrospection(
            LayerDto dto,
            IntrospectionResult.SpatialTable table) {
        return new LayerRecord(
                dto,
                table.schema(),
                table.table(),
                table.geometryColumn(),
                table.geometryType(),
                table.srid(),
                table.rowCount(),
                table.extent(),
                "ACTIVE",
                SyncConfig.disabled()
        );
    }

    /**
     * Return a copy with updated sync config.
     */
    public LayerRecord withSyncConfig(SyncConfig config) {
        return new LayerRecord(layer, sourceSchema, sourceTable, geometryColumn,
                geometryType, srid, rowCount, extent, status, config);
    }
}
