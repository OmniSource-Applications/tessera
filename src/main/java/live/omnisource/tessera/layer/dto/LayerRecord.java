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
        String status              // ACTIVE, DISABLED
) {
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
                "ACTIVE"
        );
    }
}
