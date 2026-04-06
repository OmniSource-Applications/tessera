package live.omnisource.tessera.catalog.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import live.omnisource.tessera.catalog.QueryCatalogService;
import live.omnisource.tessera.catalog.QueryExecutor;
import live.omnisource.tessera.catalog.QueryExecutor.QueryResult;
import live.omnisource.tessera.catalog.entity.CatalogEntry;
import live.omnisource.tessera.exceptions.DataStoreValidationException;
import lombok.extern.slf4j.Slf4j;

/**
 * External REST API for the query catalog.
 *
 * <h3>Endpoints:</h3>
 *
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
 *
 * <pre>{@code
 * POST /api/queries/features.by_bbox/execute
 * Content-Type: application/json
 * {
 *   "minLon": -74.1, "minLat": 40.6,
 *   "maxLon": -73.8, "maxLat": 40.9,
 *   "limit": 500
 * }
 *
 * GET /api/queries/h3.cell_features/execute?h3CellAddress=872a1072fffffff&resolution=7
 * }</pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/queries")
public class QueryApiController {

  private final QueryCatalogService catalogService;
  private final QueryExecutor executor;
  private final ObjectMapper objectMapper;

  /**
   * Constructs a new {@code QueryApiController} to handle API operations for the query catalog.
   *
   * @param catalogService the service responsible for managing catalog queries and operations
   * @param executor the query executor responsible for executing catalog queries
   * @param objectMapper the object mapper used for JSON serialization and deserialization
   */
  public QueryApiController(
      QueryCatalogService catalogService, QueryExecutor executor, ObjectMapper objectMapper) {
    this.catalogService = catalogService;
    this.executor = executor;
    this.objectMapper = objectMapper;
  }

  /**
   * Retrieves a list of catalog summaries. Optionally, the results can be filtered by category.
   *
   * @param category the category to filter the catalog entries by. If null, all entries are
   *     returned.
   * @return a list of catalog summaries with basic details for each catalog entry.
   */
  @GetMapping
  public List<CatalogSummary> list(@RequestParam(required = false) String category) {
    final var entries =
        category != null ? catalogService.listByCategory(category) : catalogService.listAll();

    return entries.stream().map(CatalogSummary::from).toList();
  }

  /**
   * Retrieves the details of a catalog entry by its name.
   *
   * @param name the name of the catalog entry to retrieve
   * @return a {@code ResponseEntity} containing the catalog entry details if found, or {@code
   *     ResponseEntity.notFound()} if no entry exists with the specified name
   */
  @GetMapping("/{name}")
  public ResponseEntity<CatalogDetail> getByName(@PathVariable String name) {
    return catalogService
        .findByName(name)
        .map(e -> ResponseEntity.ok(CatalogDetail.from(e)))
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Handles the creation of a new catalog entry.
   *
   * @param entry the catalog entry to be created, provided in the request body
   * @return a ResponseEntity containing the created catalog entry details with a status of CREATED,
   *     or a bad request status if validation fails
   */
  @PostMapping
  public ResponseEntity<CatalogDetail> create(@RequestBody CatalogEntry entry) {
    try {
      final var created = catalogService.create(entry);
      return ResponseEntity.status(HttpStatus.CREATED).body(CatalogDetail.from(created));
    } catch (DataStoreValidationException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  /**
   * Updates an existing catalog entry identified by its unique ID.
   *
   * @param id The unique identifier of the catalog entry to update.
   * @param entry The details of the catalog entry to update.
   * @return A ResponseEntity containing the updated catalog entry if the update is successful, or a
   *     bad request response if validation fails.
   */
  @PutMapping("/{id}")
  public ResponseEntity<CatalogDetail> update(
      @PathVariable UUID id, @RequestBody CatalogEntry entry) {
    try {
      final var updated = catalogService.update(id, entry);
      return ResponseEntity.ok(CatalogDetail.from(updated));
    } catch (DataStoreValidationException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  /**
   * Deletes a resource identified by its unique ID.
   *
   * @param id The unique identifier of the resource to be deleted.
   * @return A ResponseEntity indicating the outcome of the delete operation. Returns a 204 No
   *     Content response if the deletion was successful. Returns a 404 Not Found response if the
   *     resource does not exist.
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    try {
      catalogService.delete(id);
      return ResponseEntity.noContent().build();
    } catch (DataStoreValidationException e) {
      return ResponseEntity.notFound().build();
    }
  }

  /**
   * Handles POST requests to execute a query associated with a specific name.
   *
   * @param name the name of the resource or query to execute; must not be null
   * @param params an optional map of parameters to be passed for execution; can be null
   * @return a ResponseEntity containing a map of the results from the executed query
   */
  @PostMapping("/{name}/execute")
  public ResponseEntity<Map<String, Object>> executePost(
      @PathVariable String name, @RequestBody(required = false) Map<String, Object> params) {

    return executeQuery(name, params != null ? params : Map.of());
  }

  /**
   * Execute a catalog query by name with query string params. All values arrive as strings and are
   * coerced by the executor.
   */
  @GetMapping("/{name}/execute")
  public ResponseEntity<Map<String, Object>> executeGet(
      @PathVariable String name, @RequestParam Map<String, String> params) {

    return executeQuery(name, new HashMap<>(params));
  }

  private ResponseEntity<Map<String, Object>> executeQuery(
      String name, Map<String, Object> params) {
    final var entry = catalogService.findByName(name);
    if (entry.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    try {
      final QueryResult result = executor.execute(entry.get(), params);

      if (!result.success()) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                Map.of(
                    "query",
                    name,
                    "success",
                    false,
                    "error",
                    result.errorMessage(),
                    "elapsedMs",
                    result.elapsedMs()));
      }

      return ResponseEntity.ok(
          Map.of(
              "query", name,
              "success", true,
              "rowCount", result.rowCount(),
              "elapsedMs", result.elapsedMs(),
              "streaming", result.streaming(),
              "rows", result.rows()));

    } catch (DataStoreValidationException e) {
      return ResponseEntity.badRequest()
          .body(Map.of("query", name, "success", false, "error", e.getMessage()));
    }
  }

  /**
   * Represents a summary of a catalog with essential details. <br>
   * </br> This record provides an immutable data structure to encapsulate summary information about
   * a catalog entry, such as its unique identifier, name, description, category, streaming support
   * status, and associated tags.
   */
  record CatalogSummary(
      UUID id,
      String name,
      String description,
      String category,
      boolean streaming,
      List<String> tags) {
    static CatalogSummary from(CatalogEntry e) {
      return new CatalogSummary(
          e.getId(),
          e.getName(),
          e.getDescription(),
          e.getCategory(),
          e.isStreaming(),
          e.getTags());
    }
  }

  /** Represents the detailed configuration of a catalog entry, encapsulating metadata, query */
  record CatalogDetail(
      UUID id,
      String name,
      String description,
      String category,
      String querySql,
      Map<String, Object> paramSchema,
      Map<String, Object> resultSchema,
      int timeoutMs,
      boolean streaming,
      Integer cacheTtlSec,
      List<String> tags) {
    static CatalogDetail from(CatalogEntry e) {
      return new CatalogDetail(
          e.getId(),
          e.getName(),
          e.getDescription(),
          e.getCategory(),
          e.getQuerySql(),
          e.getParamSchema(),
          e.getResultSchema(),
          e.getTimeoutMs(),
          e.isStreaming(),
          e.getCacheTtlSec(),
          e.getTags());
    }
  }
}
