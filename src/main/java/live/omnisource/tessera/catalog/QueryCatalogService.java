package live.omnisource.tessera.catalog;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import live.omnisource.tessera.catalog.entity.CatalogEntry;
import live.omnisource.tessera.catalog.repository.CatalogRepository;
import live.omnisource.tessera.exceptions.DataStoreValidationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the query catalog — CRUD operations with validation.
 *
 * <p>Query names follow a dotted convention: {@code category.operation} (e.g. "features.by_bbox",
 * "h3.density_grid"). Names must be unique and match the pattern {@code [a-z][a-z0-9_.]{1,127}}.
 */
@Slf4j
@Service
@Transactional
public class QueryCatalogService {

  private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9_.]{1,127}$");
  private static final Set<String> VALID_CATEGORIES = Set.of("STATIC", "H3", "LIVE", "CUSTOM");

  /** SQL keywords that must not appear in catalog queries (write operations). */
  private static final Set<String> BLOCKED_KEYWORDS =
      Set.of(
          "INSERT",
          "UPDATE",
          "DELETE",
          "DROP",
          "ALTER",
          "TRUNCATE",
          "CREATE",
          "GRANT",
          "REVOKE",
          "COPY",
          "EXECUTE");

  private final CatalogRepository repo;

  /**
   * Constructs an instance of the QueryCatalogService.
   *
   * @param repo the repository interface used for managing catalog entries in the data store
   */
  public QueryCatalogService(CatalogRepository repo) {
    this.repo = repo;
  }

  /**
   * Retrieves all catalog entries, sorts them first by category and then by name, and returns the
   * sorted list.
   *
   * @return a list of all catalog entries sorted by category and name
   */
  @Transactional(readOnly = true)
  public List<CatalogEntry> listAll() {
    return repo.findAll().stream()
        .sorted(
            Comparator.comparing(CatalogEntry::getCategory).thenComparing(CatalogEntry::getName))
        .toList();
  }

  /**
   * Retrieves a list of catalog entries filtered by the specified category, sorted by name in
   * ascending order.
   *
   * @param category the category of catalog entries to filter by
   * @return a list of catalog entries in the specified category, sorted by name in ascending order
   */
  @Transactional(readOnly = true)
  public List<CatalogEntry> listByCategory(String category) {
    return repo.findByCategoryOrderByNameAsc(category.toUpperCase());
  }

  /**
   * Finds a catalog entry by its name.
   *
   * @param name the name of the catalog entry to search for
   * @return an {@code Optional} containing the catalog entry if found, or an empty {@code Optional}
   *     if no catalog entry with the specified name exists
   */
  @Transactional(readOnly = true)
  public Optional<CatalogEntry> findByName(String name) {
    return repo.findByName(name);
  }

  /**
   * Retrieves a catalog entry by its name.
   *
   * @param name the name of the catalog entry to retrieve
   * @return the catalog entry matching the specified name
   * @throws DataStoreValidationException if no catalog entry with the specified name exists
   */
  @Transactional(readOnly = true)
  public CatalogEntry getByName(String name) {
    return repo.findByName(name)
        .orElseThrow(() -> new DataStoreValidationException("Query not found: " + name));
  }

  /**
   * Retrieves a catalog entry by its unique identifier.
   *
   * @param id the unique identifier of the catalog entry to retrieve
   * @return an {@code Optional} containing the catalog entry if found, or an empty {@code Optional}
   *     if no catalog entry with the specified identifier exists
   */
  @Transactional(readOnly = true)
  public Optional<CatalogEntry> findById(UUID id) {
    return repo.findById(id);
  }

  /**
   * Creates a new catalog entry in the system after validation.
   *
   * @param entry the catalog entry to create
   * @return the created catalog entry after being saved
   */
  public CatalogEntry create(CatalogEntry entry) {
    validate(entry, true);
    log.info("Creating catalog entry: {}", entry.getName());
    return repo.save(entry);
  }

  /**
   * Updates an existing catalog entry with new values provided in the updates object.
   *
   * <p>The method fetches the existing catalog entry by its unique identifier and applies the
   * updates while ensuring proper validation. If the catalog entry's name is being updated, the new
   * name must be unique. Validation errors or non-existent entries will result in exceptions.
   *
   * @param id the unique identifier of the catalog entry to update
   * @param updates the catalog entry object containing the new values to update the existing
   *     catalog entry
   * @return the updated catalog entry after it has been persisted to the data store
   * @throws DataStoreValidationException if the catalog entry does not exist, the name is invalid
   *     or already in use, or any of the updated fields fail validation
   */
  public CatalogEntry update(UUID id, CatalogEntry updates) {
    final var existing =
        repo.findById(id)
            .orElseThrow(() -> new DataStoreValidationException("Query not found: " + id));

    if (!existing.getName().equals(updates.getName()) && repo.existsByName(updates.getName())) {
      throw new DataStoreValidationException("Name already taken: " + updates.getName());
    }

    existing.setName(updates.getName());
    existing.setDescription(updates.getDescription());
    existing.setCategory(updates.getCategory());
    existing.setQuerySql(updates.getQuerySql());
    existing.setParamSchema(updates.getParamSchema());
    existing.setResultSchema(updates.getResultSchema());
    existing.setTimeoutMs(updates.getTimeoutMs());
    existing.setStreaming(updates.isStreaming());
    existing.setCacheTtlSec(updates.getCacheTtlSec());
    existing.setTags(updates.getTags());

    validate(existing, false);
    return repo.save(existing);
  }

  /**
   * Deletes a catalog entry identified by the provided unique identifier.
   *
   * <p>This method checks whether an entry with the specified identifier exists in the data store.
   * If no such entry exists, a {@code DataStoreValidationException} is thrown. If the entry is
   * found, it is deleted, and a log entry is recorded indicating the deletion.
   *
   * @param id the unique identifier of the catalog entry to delete
   * @throws DataStoreValidationException if no catalog entry with the specified identifier exists
   */
  public void delete(UUID id) {
    if (!repo.existsById(id)) {
      throw new DataStoreValidationException("Query not found: " + id);
    }
    repo.deleteById(id);
    log.info("Deleted catalog entry: {}", id);
  }

  private void validate(CatalogEntry entry, boolean isNew) {
    if (entry.getName() == null || !NAME_PATTERN.matcher(entry.getName()).matches()) {
      throw new DataStoreValidationException(
          "Invalid query name. Must match: " + NAME_PATTERN.pattern());
    }

    if (isNew && repo.existsByName(entry.getName())) {
      throw new DataStoreValidationException("Query name already exists: " + entry.getName());
    }

    if (entry.getCategory() == null
        || !VALID_CATEGORIES.contains(entry.getCategory().toUpperCase())) {
      throw new DataStoreValidationException(
          "Invalid category. Must be one of: " + VALID_CATEGORIES);
    }
    entry.setCategory(entry.getCategory().toUpperCase());

    if (entry.getQuerySql() == null || entry.getQuerySql().isBlank()) {
      throw new DataStoreValidationException("Query SQL is required.");
    }

    final String sqlUpper = entry.getQuerySql().toUpperCase();
    for (String kw : BLOCKED_KEYWORDS) {
      if (sqlUpper.matches(".*\\b" + kw + "\\b.*")) {
        throw new DataStoreValidationException(
            "Catalog queries must be read-only. Blocked keyword: " + kw);
      }
    }

    if (entry.getTimeoutMs() < 100 || entry.getTimeoutMs() > 300_000) {
      throw new DataStoreValidationException("Timeout must be between 100ms and 300000ms.");
    }
  }
}
