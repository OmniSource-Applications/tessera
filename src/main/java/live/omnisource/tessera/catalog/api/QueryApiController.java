package live.omnisource.tessera.catalog.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import live.omnisource.tessera.catalog.QueryCatalogService;
import live.omnisource.tessera.catalog.QueryExecutor;
import live.omnisource.tessera.catalog.QueryExecutor.QueryResult;
import live.omnisource.tessera.catalog.entity.CatalogEntry;
import live.omnisource.tessera.exceptions.DataStoreValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * External REST API for the query catalog.
 *
 * <h3>Endpoints:</h3>
 * <pre>
 *   GET    /api/queries                       — list all catalog entries
 *   GET    /api/queries?category=H3            — filter by category
 *   GET    /api/queries/{name}                — get a single entry by name
 *   POST   /api/queries/{name}/execute        — execute a query with params
 *   GET    /api/queries/{name}/execute?p=...  — execute via query string
 *
 *   POST   /api/queries                       — create a new catalog entry
 *   PUT    /api/queries/{id}                  — update an entry
 *   DELETE /api/queries/{id}                  — delete an entry
 * </pre>
 *
 * <h3>Execution examples:</h3>
 * <pre>{@code
 *   POST /api/queries/features.by_bbox/execute
 *   Content-Type: application/json
 *   {
 *     "minLon": -74.1, "minLat": 40.6,
 *     "maxLon": -73.8, "maxLat": 40.9,
 *     "limit": 500
 *   }
 *
 *   GET /api/queries/h3.cell_features/execute?h3CellAddress=872a1072fffffff&resolution=7
 * }</pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/queries")
public class QueryApiController {

    private final QueryCatalogService catalogService;
    private final QueryExecutor executor;
    private final ObjectMapper objectMapper;

    public QueryApiController(QueryCatalogService catalogService,
                              QueryExecutor executor,
                              ObjectMapper objectMapper) {
        this.catalogService = catalogService;
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    // ── Catalog CRUD ─────────────────────────────────────────

    @GetMapping
    public List<CatalogSummary> list(@RequestParam(required = false) String category) {
        var entries = category != null
                ? catalogService.listByCategory(category)
                : catalogService.listAll();

        return entries.stream().map(CatalogSummary::from).toList();
    }

    @GetMapping("/{name}")
    public ResponseEntity<CatalogDetail> getByName(@PathVariable String name) {
        return catalogService.findByName(name)
                .map(e -> ResponseEntity.ok(CatalogDetail.from(e)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CatalogDetail> create(@RequestBody CatalogEntry entry) {
        try {
            var created = catalogService.create(entry);
            return ResponseEntity.status(HttpStatus.CREATED).body(CatalogDetail.from(created));
        } catch (DataStoreValidationException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<CatalogDetail> update(@PathVariable UUID id,
                                                @RequestBody CatalogEntry entry) {
        try {
            var updated = catalogService.update(id, entry);
            return ResponseEntity.ok(CatalogDetail.from(updated));
        } catch (DataStoreValidationException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        try {
            catalogService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (DataStoreValidationException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Query Execution ──────────────────────────────────────

    /**
     * Execute a catalog query by name with JSON body params.
     */
    @PostMapping("/{name}/execute")
    public ResponseEntity<Map<String, Object>> executePost(
            @PathVariable String name,
            @RequestBody(required = false) Map<String, Object> params) {

        return executeQuery(name, params != null ? params : Map.of());
    }

    /**
     * Execute a catalog query by name with query string params.
     * All values arrive as strings and are coerced by the executor.
     */
    @GetMapping("/{name}/execute")
    public ResponseEntity<Map<String, Object>> executeGet(
            @PathVariable String name,
            @RequestParam Map<String, String> params) {

        return executeQuery(name, new HashMap<>(params));
    }

    private ResponseEntity<Map<String, Object>> executeQuery(
            String name, Map<String, Object> params) {
        var entry = catalogService.findByName(name);
        if (entry.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            QueryResult result = executor.execute(entry.get(), params);

            if (!result.success()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of(
                                "query", name,
                                "success", false,
                                "error", result.errorMessage(),
                                "elapsedMs", result.elapsedMs()
                        ));
            }

            return ResponseEntity.ok(Map.of(
                    "query", name,
                    "success", true,
                    "rowCount", result.rowCount(),
                    "elapsedMs", result.elapsedMs(),
                    "streaming", result.streaming(),
                    "rows", result.rows()
            ));

        } catch (DataStoreValidationException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "query", name,
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    // ── Response DTOs ────────────────────────────────────────

    record CatalogSummary(
            UUID id, String name, String description, String category,
            boolean streaming, List<String> tags
    ) {
        static CatalogSummary from(CatalogEntry e) {
            return new CatalogSummary(e.getId(), e.getName(), e.getDescription(),
                    e.getCategory(), e.isStreaming(), e.getTags());
        }
    }

    record CatalogDetail(
            UUID id, String name, String description, String category,
            String querySql, Map<String, Object> paramSchema,
            Map<String, Object> resultSchema, int timeoutMs,
            boolean streaming, Integer cacheTtlSec, List<String> tags
    ) {
        static CatalogDetail from(CatalogEntry e) {
            return new CatalogDetail(e.getId(), e.getName(), e.getDescription(),
                    e.getCategory(), e.getQuerySql(), e.getParamSchema(),
                    e.getResultSchema(), e.getTimeoutMs(), e.isStreaming(),
                    e.getCacheTtlSec(), e.getTags());
        }
    }
}