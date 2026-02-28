package live.omnisource.tessera.sync;

import live.omnisource.tessera.model.dto.RawRecord;
import live.omnisource.tessera.sync.dto.ExtractedFeature;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Transforms RawRecords from external sources into ExtractedFeatures.
 *
 * Knows how to find the geometry in different source types:
 *   - JDBC (PostGIS/MySQL/Oracle): single geometry column name
 *   - Cassandra: comma-separated lat,lng column pair
 *
 * Builds a stable external ID from one or more PK columns, falling back
 * to a hash of all fields if no PK is available.
 */
@Slf4j
public class FeatureExtractor {

    private final String geometryColumn;  // "geom" or "lat,lng"
    private final String[] pkColumns;     // columns to combine for external_id
    private final boolean isLatLngPair;

    /**
     * @param geometryColumn geometry column name, or "lat,lng" for Cassandra
     * @param pkColumns      primary key column name(s); if empty, hash-based IDs
     */
    public FeatureExtractor(String geometryColumn, String... pkColumns) {
        this.geometryColumn = geometryColumn;
        this.isLatLngPair = geometryColumn != null && geometryColumn.contains(",");
        this.pkColumns = pkColumns != null && pkColumns.length > 0 ? pkColumns : null;
    }

    /**
     * Extract a feature from a raw record.
     * Returns null if the geometry is missing or unparseable.
     */
    public ExtractedFeature extract(RawRecord raw) {
        // 1. Extract geometry
        Geometry geom;
        if (isLatLngPair) {
            String[] parts = geometryColumn.split(",");
            Object lat = raw.get(parts[0].trim());
            Object lng = raw.get(parts[1].trim());
            geom = GeometryConverter.fromLatLng(lat, lng);
        } else {
            geom = GeometryConverter.convert(raw.get(geometryColumn));
        }

        if (geom == null) {
            log.trace("Skipping record with null/invalid geometry in {}.{}", raw.schema(), raw.table());
            return null;
        }

        // 2. Build attributes (everything except the geometry column(s))
        Map<String, Object> attributes = new LinkedHashMap<>();
        for (var entry : raw.fields().entrySet()) {
            String col = entry.getKey();
            if (isGeometryColumn(col)) continue;
            Object val = entry.getValue();
            // Serialize non-trivial types to string for JSONB safety
            if (val != null && !isJsonSafe(val)) {
                val = val.toString();
            }
            attributes.put(col, val);
        }

        // 3. Build external ID
        String externalId = buildExternalId(raw);

        // 4. Hash for change detection
        byte[] hash = hashAttributes(attributes);

        return new ExtractedFeature(externalId, geom, attributes, hash);
    }

    private boolean isGeometryColumn(String col) {
        if (isLatLngPair) {
            for (String part : geometryColumn.split(",")) {
                if (col.equalsIgnoreCase(part.trim())) return true;
            }
            return false;
        }
        return col.equalsIgnoreCase(geometryColumn);
    }

    private String buildExternalId(RawRecord raw) {
        if (pkColumns != null) {
            var sb = new StringBuilder();
            for (int i = 0; i < pkColumns.length; i++) {
                if (i > 0) sb.append(':');
                Object val = raw.get(pkColumns[i]);
                sb.append(val != null ? val.toString() : "null");
            }
            return sb.toString();
        }
        // Fallback: hash all fields
        return Integer.toHexString(raw.fields().hashCode());
    }

    private static boolean isJsonSafe(Object val) {
        return val instanceof String ||
                val instanceof Number ||
                val instanceof Boolean ||
                val instanceof Map ||
                val instanceof Iterable;
    }

    private static byte[] hashAttributes(Map<String, Object> attrs) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (var entry : attrs.entrySet()) {
                md.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
                if (entry.getValue() != null) {
                    md.update(entry.getValue().toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}