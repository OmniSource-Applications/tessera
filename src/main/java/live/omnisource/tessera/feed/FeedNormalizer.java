package live.omnisource.tessera.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import live.omnisource.tessera.feed.dto.FeedConfig;
import live.omnisource.tessera.feed.dto.FeedConfig.*;
import live.omnisource.tessera.sync.GeometryConverter;
import live.omnisource.tessera.sync.dto.ExtractedFeature;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.*;

/**
 * Converts raw feed messages into {@link ExtractedFeature} objects using the
 * feed's {@link SchemaMapping} configuration.
 *
 * <p>Handles JSON, JSON Lines, GeoJSON, CSV, and raw text. Geometry is extracted
 * according to the mapping (lat/lng, WKT, WKB, GeoJSON field, or H3 centroid).</p>
 */
@Slf4j
@Component
public class FeedNormalizer {

    private final ObjectMapper objectMapper;

    public FeedNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parse a raw message into zero or more features.
     *
     * @param raw    the raw message string
     * @param schema the schema mapping from the feed config
     * @return list of extracted features (may be empty if parsing fails)
     */
    public List<ExtractedFeature> normalize(String raw, SchemaMapping schema) {
        if (raw == null || raw.isBlank()) return List.of();

        try {
            var result = switch (schema.format()) {
                case JSON -> normalizeJsonObject(raw, schema);
                case JSON_LINES -> normalizeJsonLines(raw, schema);
                case JSON_ARRAY -> normalizeJsonArray(raw, schema);
                case GEOJSON -> normalizeGeoJson(raw, schema);
                case CSV -> normalizeCsv(raw, schema);
                case RAW_TEXT -> normalizeRawText(raw, schema);
                default -> {
                    log.warn("Unsupported format: {}", schema.format());
                    yield List.of();
                }
            };
            if (result.isEmpty()) {
                log.debug("Normalizer produced 0 features for format={}, rootPath={}, message length={}",
                        schema.format(), schema.rootPath(), raw.length());
            }
            return (List<ExtractedFeature>) result;
        } catch (Exception e) {
            log.warn("Failed to normalize message (format={}, length={}): {}",
                    schema.format(), raw.length(), e.getMessage());
            return List.of();
        }
    }

    // ── Format handlers ─────────────────────────────────

    private List<ExtractedFeature> normalizeJsonObject(String raw, SchemaMapping schema) throws Exception {
        JsonNode node = objectMapper.readTree(raw);

        // If rootPath is set, navigate to it
        if (schema.rootPath() != null && !schema.rootPath().isBlank()) {
            JsonNode resolved = navigatePath(node, schema.rootPath());
            if (resolved == null) {
                log.debug("rootPath '{}' not found in JSON. Top-level keys: {}",
                        schema.rootPath(), iteratorToList(node.fieldNames()));
                return List.of();
            }
            node = resolved;
            if (node.isArray()) return normalizeArray((ArrayNode) node, schema);
        }

        if (node.isObject()) {
            return extractFromNode(node, schema).map(List::of).orElse(List.of());
        }
        if (node.isArray()) {
            return normalizeArray((ArrayNode) node, schema);
        }
        return List.of();
    }

    /** Collect iterator to list for logging */
    private static List<String> iteratorToList(java.util.Iterator<String> it) {
        var list = new ArrayList<String>();
        it.forEachRemaining(list::add);
        return list;
    }

    private List<ExtractedFeature> normalizeJsonLines(String raw, SchemaMapping schema) {
        var features = new ArrayList<ExtractedFeature>();
        for (String line : raw.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                JsonNode node = objectMapper.readTree(line);
                extractFromNode(node, schema).ifPresent(features::add);
            } catch (Exception e) {
                log.trace("Skipping invalid JSON line: {}", e.getMessage());
            }
        }
        return features;
    }

    private List<ExtractedFeature> normalizeJsonArray(String raw, SchemaMapping schema) throws Exception {
        JsonNode node = objectMapper.readTree(raw);
        if (schema.rootPath() != null) {
            node = navigatePath(node, schema.rootPath());
        }
        if (node != null && node.isArray()) {
            return normalizeArray((ArrayNode) node, schema);
        }
        return List.of();
    }

    private List<ExtractedFeature> normalizeGeoJson(String raw, SchemaMapping schema) throws Exception {
        JsonNode root = objectMapper.readTree(raw);
        String type = root.has("type") ? root.get("type").asText() : "";

        if ("FeatureCollection".equals(type)) {
            var features = new ArrayList<ExtractedFeature>();
            ArrayNode arr = (ArrayNode) root.get("features");
            if (arr != null) {
                for (JsonNode featureNode : arr) {
                    extractGeoJsonFeature(featureNode, schema).ifPresent(features::add);
                }
            }
            return features;
        } else if ("Feature".equals(type)) {
            return extractGeoJsonFeature(root, schema).map(List::of).orElse(List.of());
        }
        return List.of();
    }

    private List<ExtractedFeature> normalizeCsv(String raw, SchemaMapping schema) {
        String[] lines = raw.split("\n");
        if (lines.length < 2) return List.of();

        String[] headers = lines[0].split(",");
        var features = new ArrayList<ExtractedFeature>();

        for (int i = 1; i < lines.length; i++) {
            String[] values = lines[i].split(",", -1);
            if (values.length != headers.length) continue;

            var record = new LinkedHashMap<String, Object>();
            for (int j = 0; j < headers.length; j++) {
                record.put(headers[j].trim(), values[j].trim());
            }
            extractFromMap(record, schema).ifPresent(features::add);
        }
        return features;
    }

    private List<ExtractedFeature> normalizeRawText(String raw, SchemaMapping schema) {
        var attrs = Map.<String, Object>of("raw", raw, "receivedAt", java.time.Instant.now().toString());
        // No geometry for raw text
        return List.of(new ExtractedFeature(
                UUID.randomUUID().toString(), null, attrs, hash(raw).getBytes()
        ));
    }

    // ── Extraction ──────────────────────────────────────

    private List<ExtractedFeature> normalizeArray(ArrayNode array, SchemaMapping schema) {
        var features = new ArrayList<ExtractedFeature>();
        for (JsonNode node : array) {
            extractFromNode(node, schema).ifPresent(features::add);
        }
        return features;
    }

    private Optional<ExtractedFeature> extractFromNode(JsonNode node, SchemaMapping schema) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = objectMapper.convertValue(node, Map.class);
        return extractFromMap(map, schema);
    }

    private Optional<ExtractedFeature> extractFromMap(Map<String, Object> record, SchemaMapping schema) {
        // Extract geometry
        Geometry geometry = extractGeometry(record, schema.geometry());

        // Extract external ID (supports dot-notation)
        String externalId;
        if (schema.idField() != null) {
            Object idVal = resolveField(record, schema.idField());
            externalId = idVal != null ? String.valueOf(idVal) : UUID.randomUUID().toString();
        } else {
            externalId = UUID.randomUUID().toString();
        }

        // Build attributes
        Map<String, Object> attributes = buildAttributes(record, schema);

        // Compute hash for dedup
        String dataHash = hash(record.toString());

        return Optional.of(new ExtractedFeature(externalId, geometry, attributes, dataHash.getBytes()));
    }

    private Optional<ExtractedFeature> extractGeoJsonFeature(JsonNode featureNode, SchemaMapping schema) {
        try {
            JsonNode geomNode = featureNode.get("geometry");
            Geometry geometry = null;
            if (geomNode != null && !geomNode.isNull()) {
                geometry = GeometryConverter.convert(objectMapper.writeValueAsString(geomNode));
            }

            JsonNode props = featureNode.get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> attributes = props != null
                    ? objectMapper.convertValue(props, Map.class) : Map.of();

            String externalId = featureNode.has("id")
                    ? featureNode.get("id").asText()
                    : UUID.randomUUID().toString();

            return Optional.of(new ExtractedFeature(externalId, geometry, attributes, hash(featureNode.toString()).getBytes()));
        } catch (Exception e) {
            log.trace("Failed to extract GeoJSON feature: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ── Geometry extraction ─────────────────────────────

    private Geometry extractGeometry(Map<String, Object> record, GeometryMapping mapping) {
        if (mapping == null || mapping.type() == GeometryType.NONE) return null;

        try {
            return switch (mapping.type()) {
                case LAT_LNG -> {
                    Object lat = resolveField(record, mapping.latField());
                    Object lng = resolveField(record, mapping.lngField());
                    if (lat == null || lng == null) {
                        log.trace("LAT_LNG: lat={} lng={} (fields: {}, {}). Available keys: {}",
                                lat, lng, mapping.latField(), mapping.lngField(), record.keySet());
                    }
                    yield GeometryConverter.fromLatLng(toNumber(lat), toNumber(lng));
                }
                case WKT, WKB -> GeometryConverter.convert(resolveField(record, mapping.geometryField()));
                case GEOJSON -> {
                    Object geojson = resolveField(record, mapping.geometryField());
                    if (geojson instanceof Map) {
                        yield GeometryConverter.convert(objectMapper.writeValueAsString(geojson));
                    }
                    yield GeometryConverter.convert(geojson);
                }
                default -> null;
            };
        } catch (Exception e) {
            log.debug("Geometry extraction failed (type={}): {}", mapping.type(), e.getMessage());
            return null;
        }
    }

    /**
     * Resolve a field value from a record, supporting dot-notation for nested access.
     * E.g. "Message.PositionReport.Latitude" will traverse nested Maps.
     */
    @SuppressWarnings("unchecked")
    private Object resolveField(Map<String, Object> record, String fieldPath) {
        if (fieldPath == null || record == null) return null;

        // Try direct lookup first (fast path)
        if (record.containsKey(fieldPath)) return record.get(fieldPath);

        // Try dot-notation traversal
        if (fieldPath.contains(".")) {
            String[] parts = fieldPath.split("\\.");
            Object current = record;
            for (String part : parts) {
                if (current instanceof Map) {
                    current = ((Map<String, Object>) current).get(part);
                } else {
                    return null;
                }
            }
            return current;
        }

        return null;
    }

    // ── Attribute building ──────────────────────────────

    private Map<String, Object> buildAttributes(Map<String, Object> record, SchemaMapping schema) {
        var attrs = new LinkedHashMap<String, Object>();

        Set<String> excludes = new HashSet<>();
        if (schema.excludeFields() != null) excludes.addAll(schema.excludeFields());
        // Always exclude raw geometry fields from attributes
        if (schema.geometry() != null) {
            if (schema.geometry().latField() != null) excludes.add(schema.geometry().latField());
            if (schema.geometry().lngField() != null) excludes.add(schema.geometry().lngField());
            if (schema.geometry().geometryField() != null) excludes.add(schema.geometry().geometryField());
        }

        if (schema.attributeFields() != null && !schema.attributeFields().isEmpty()) {
            // Include only specified fields
            for (String field : schema.attributeFields()) {
                if (record.containsKey(field)) attrs.put(field, record.get(field));
            }
        } else {
            // Include all except excluded
            for (var entry : record.entrySet()) {
                if (!excludes.contains(entry.getKey())) {
                    attrs.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return attrs;
    }

    // ── Helpers ─────────────────────────────────────────

    private JsonNode navigatePath(JsonNode root, String path) {
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            if (current == null || !current.has(segment)) return null;
            current = current.get(segment);
        }
        return current;
    }

    private Number toNumber(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n;
        try { return Double.parseDouble(val.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private String hash(String data) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] h = digest.digest(data.getBytes());
            return live.omnisource.tessera.filestore.crypto.Hex.toHex(h);
        } catch (Exception e) {
            return null;
        }
    }
}