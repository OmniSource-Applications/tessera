package live.omnisource.tessera.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import live.omnisource.tessera.sync.dto.ExtractedFeature;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * High-performance batch writer for geo_features and h3_cell_index.
 *
 * Uses raw JDBC batch inserts (not JPA) for throughput.
 * Geometry is written as WKB via ST_GeomFromWKB with SRID 4326.
 * H3 indexing is computed server-side using the PostgreSQL h3 extension.
 */
@Slf4j
@Component
public class FeatureBatchWriter {

    private static final int BATCH_SIZE = 500;

    private static final String INSERT_FEATURE_RETURNING = """
            INSERT INTO tessera.geo_features
                (source_id, external_id, source_table, geometry, geometry_type, attributes, data_hash, updated_at)
            VALUES
                (?::uuid, ?, ?, ST_GeomFromWKB(?, 4326), ?, ?::jsonb, ?, now())
            RETURNING id, ingested_at
            """;

    /**
     * H3 index insert: compute the h3 cell from a lat/lng centroid at the given resolution.
     * Uses the PG h3 extension's h3_lat_lng_to_cell function.
     */
    private static final String INSERT_H3 = """
            INSERT INTO tessera.h3_cell_index
                (feature_id, feature_ingest, resolution, h3_index, h3_index_int, center_lat, center_lng)
            SELECT
                ?, ?, ?,
                h3_lat_lng_to_cell(ST_MakePoint(?, ?)::point, ?),
                h3_lat_lng_to_cell(ST_MakePoint(?, ?)::point, ?)::bigint,
                ?, ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate txTemplate;
    private final ObjectMapper objectMapper;
    private final WKBWriter wkbWriter;

    public FeatureBatchWriter(JdbcTemplate jdbcTemplate,
                              TransactionTemplate txTemplate,
                              ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.txTemplate = txTemplate;
        this.objectMapper = objectMapper;
        this.wkbWriter = new WKBWriter(2, true); // 2D, include SRID
    }

    /**
     * Write a batch of features and their H3 indexes in a single transaction.
     *
     * @param sourceId      UUID of the external_source record
     * @param sourceTable   qualified source table name (e.g. "public.points_of_interest")
     * @param features      list of extracted features to write
     * @param h3Resolutions H3 resolutions to index at (e.g. [7, 9])
     * @return number of features written
     */
    public int writeBatch(UUID sourceId, String sourceTable,
                          List<ExtractedFeature> features, int[] h3Resolutions) {
        if (features.isEmpty()) return 0;

        return txTemplate.execute(status -> {
            long[] featureIds = new long[features.size()];
            Timestamp[] ingestTimes = new Timestamp[features.size()];
            int written = 0;

            // Insert each feature and capture the generated id + ingested_at
            for (int i = 0; i < features.size(); i++) {
                ExtractedFeature f = features.get(i);
                var row = jdbcTemplate.queryForMap(INSERT_FEATURE_RETURNING,
                        sourceId.toString(),
                        f.externalId(),
                        sourceTable,
                        wkbWriter.write(f.geometry()),
                        f.geometry().getGeometryType().toUpperCase(),
                        toJson(f.attributes()),
                        f.dataHash());

                featureIds[i] = ((Number) row.get("id")).longValue();
                ingestTimes[i] = (Timestamp) row.get("ingested_at");
                written++;
            }

            // H3 index for each feature at each resolution
            if (h3Resolutions.length > 0 && written > 0) {
                indexH3Batch(features, featureIds, ingestTimes, h3Resolutions);
            }

            log.debug("Wrote batch of {} features for {}", written, sourceTable);
            return written;
        });
    }

    private void indexH3Batch(List<ExtractedFeature> features, long[] featureIds,
                              Timestamp[] ingestTimes, int[] resolutions) {
        jdbcTemplate.execute(INSERT_H3, (PreparedStatement ps) -> {
            for (int i = 0; i < features.size(); i++) {
                Geometry geom = features.get(i).geometry();
                var centroid = geom.getCentroid();
                double lng = centroid.getX();
                double lat = centroid.getY();

                for (int res : resolutions) {
                    ps.setLong(1, featureIds[i]);
                    ps.setTimestamp(2, ingestTimes[i]);
                    ps.setInt(3, res);
                    ps.setDouble(4, lng);    // h3_lat_lng_to_cell point lng
                    ps.setDouble(5, lat);    // h3_lat_lng_to_cell point lat
                    ps.setInt(6, res);       // h3 resolution
                    ps.setDouble(7, lng);    // bigint cast duplicate
                    ps.setDouble(8, lat);
                    ps.setInt(9, res);
                    ps.setDouble(10, lat);   // center_lat
                    ps.setDouble(11, lng);   // center_lng
                    ps.addBatch();
                }
            }
            ps.executeBatch();
            return null;
        });
    }

    private String toJson(Map<String, Object> attrs) {
        try {
            return objectMapper.writeValueAsString(attrs);
        } catch (Exception e) {
            return "{}";
        }
    }
}