package live.omnisource.tessera.catalog;

import live.omnisource.tessera.catalog.entity.CatalogEntry;
import live.omnisource.tessera.catalog.repository.CatalogRepository;
import live.omnisource.tessera.exceptions.DataStoreValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Manages the query catalog — CRUD operations with validation.
 *
 * <p>Query names follow a dotted convention: {@code category.operation}
 * (e.g. "features.by_bbox", "h3.density_grid"). Names must be unique
 * and match the pattern {@code [a-z][a-z0-9_.]{1,127}}.</p>
 */
@Slf4j
@Service
@Transactional
public class QueryCatalogService {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9_.]{1,127}$");
    private static final Set<String> VALID_CATEGORIES = Set.of("STATIC", "H3", "LIVE", "CUSTOM");

    /** SQL keywords that must not appear in catalog queries (write operations). */
    private static final Set<String> BLOCKED_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "TRUNCATE",
            "CREATE", "GRANT", "REVOKE", "COPY", "EXECUTE"
    );

    private final CatalogRepository repo;

    public QueryCatalogService(CatalogRepository repo) {
        this.repo = repo;
    }

    // ── Read ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CatalogEntry> listAll() {
        return repo.findAll().stream()
                .sorted(Comparator.comparing(CatalogEntry::getCategory)
                        .thenComparing(CatalogEntry::getName))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogEntry> listByCategory(String category) {
        return repo.findByCategoryOrderByNameAsc(category.toUpperCase());
    }

    @Transactional(readOnly = true)
    public Optional<CatalogEntry> findByName(String name) {
        return repo.findByName(name);
    }

    @Transactional(readOnly = true)
    public CatalogEntry getByName(String name) {
        return repo.findByName(name).orElseThrow(
                () -> new DataStoreValidationException("Query not found: " + name));
    }

    @Transactional(readOnly = true)
    public Optional<CatalogEntry> findById(UUID id) {
        return repo.findById(id);
    }

    // ── Write ─────────────────────────────────────────────────

    public CatalogEntry create(CatalogEntry entry) {
        validate(entry, true);
        log.info("Creating catalog entry: {}", entry.getName());
        return repo.save(entry);
    }

    public CatalogEntry update(UUID id, CatalogEntry updates) {
        var existing = repo.findById(id).orElseThrow(
                () -> new DataStoreValidationException("Query not found: " + id));

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

    public void delete(UUID id) {
        if (!repo.existsById(id)) {
            throw new DataStoreValidationException("Query not found: " + id);
        }
        repo.deleteById(id);
        log.info("Deleted catalog entry: {}", id);
    }

    // ── Validation ────────────────────────────────────────────

    private void validate(CatalogEntry entry, boolean isNew) {
        if (entry.getName() == null || !NAME_PATTERN.matcher(entry.getName()).matches()) {
            throw new DataStoreValidationException(
                    "Invalid query name. Must match: " + NAME_PATTERN.pattern());
        }

        if (isNew && repo.existsByName(entry.getName())) {
            throw new DataStoreValidationException(
                    "Query name already exists: " + entry.getName());
        }

        if (entry.getCategory() == null || !VALID_CATEGORIES.contains(entry.getCategory().toUpperCase())) {
            throw new DataStoreValidationException(
                    "Invalid category. Must be one of: " + VALID_CATEGORIES);
        }
        entry.setCategory(entry.getCategory().toUpperCase());

        if (entry.getQuerySql() == null || entry.getQuerySql().isBlank()) {
            throw new DataStoreValidationException("Query SQL is required.");
        }

        // Block write operations
        String sqlUpper = entry.getQuerySql().toUpperCase();
        for (String kw : BLOCKED_KEYWORDS) {
            if (sqlUpper.matches(".*\\b" + kw + "\\b.*")) {
                throw new DataStoreValidationException(
                        "Catalog queries must be read-only. Blocked keyword: " + kw);
            }
        }

        if (entry.getTimeoutMs() < 100 || entry.getTimeoutMs() > 300_000) {
            throw new DataStoreValidationException(
                    "Timeout must be between 100ms and 300000ms.");
        }
    }
}