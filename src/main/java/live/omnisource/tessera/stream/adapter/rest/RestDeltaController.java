package live.omnisource.tessera.stream.adapter.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/stream/poll")
public class RestDeltaController {

    private static final int DEFAULT_LIMIT = 500;
    private static final int MAX_LIMIT = 5000;

    private final NamedParameterJdbcTemplate namedJdbc;

    public RestDeltaController(NamedParameterJdbcTemplate namedJdbc) {
        this.namedJdbc = namedJdbc;
    }

    @GetMapping
    public Map<String, Object> poll(
            @RequestParam String since,
            @RequestParam(required = false) UUID sourceId,
            @RequestParam(required = false) String sourceTable,
            @RequestParam(required = false) Double minX,
            @RequestParam(required = false) Double minY,
            @RequestParam(required = false) Double maxX,
            @RequestParam(required = false) Double maxY,
            @RequestParam(defaultValue = "500") int limit) {

        Instant cursor = Instant.parse(since);
        int effectiveLimit = Math.clamp(limit, 1, MAX_LIMIT);

        // Query one extra to detect hasMore
        int queryLimit = effectiveLimit + 1;

        var sql = new StringBuilder("""
                SELECT f.id, f.external_id, f.source_id, f.source_table,
                       ST_AsGeoJSON(f.geometry)::text AS geometry_json,
                       f.geometry_type, f.attributes, f.updated_at
                FROM tessera.geo_features f
                WHERE f.updated_at > :cursor
                """);

        var params = new HashMap<String, Object>();
        params.put("cursor", Timestamp.from(cursor));

        if (sourceId != null) {
            sql.append(" AND f.source_id = :sourceId::uuid");
            params.put("sourceId", sourceId.toString());
        }
        if (sourceTable != null) {
            sql.append(" AND f.source_table = :sourceTable");
            params.put("sourceTable", sourceTable);
        }
        if (minX != null && minY != null && maxX != null && maxY != null) {
            sql.append(" AND ST_Intersects(f.geometry, ST_MakeEnvelope(:minX, :minY, :maxX, :maxY, 4326))");
            params.put("minX", minX);
            params.put("minY", minY);
            params.put("maxX", maxX);
            params.put("maxY", maxY);
        }

        sql.append(" ORDER BY f.updated_at ASC LIMIT :limit");
        params.put("limit", queryLimit);

        List<Map<String, Object>> rows = namedJdbc.queryForList(sql.toString(), params);

        boolean hasMore = rows.size() > effectiveLimit;
        List<Map<String, Object>> features = hasMore
                ? rows.subList(0, effectiveLimit) : rows;

        // Compute next cursor from the last feature's updated_at
        Instant nextCursor = cursor;
        if (!features.isEmpty()) {
            Object lastUpdated = features.getLast().get("updated_at");
            if (lastUpdated instanceof Timestamp ts) {
                nextCursor = ts.toInstant();
            } else if (lastUpdated instanceof Instant inst) {
                nextCursor = inst;
            }
        }

        return Map.of(
                "count", features.size(),
                "cursor", nextCursor.toString(),
                "hasMore", hasMore,
                "features", features
        );
    }
}