package live.omnisource.tessera.layer.dto;

import java.util.List;

/**
 * Result of introspecting a data source for spatial tables.
 */
public record IntrospectionResult(
        List<SpatialTable> tables
) {

    /**
     * A single spatial table/view discovered in the data source.
     */
    public record SpatialTable(
            String schema,
            String table,
            String geometryColumn,
            String geometryType,
            int srid,
            long rowCount,
            double[] extent
    ) {
        public String qualifiedName() {
            return schema + "." + table;
        }
    }
}