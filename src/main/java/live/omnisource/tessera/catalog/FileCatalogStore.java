package live.omnisource.tessera.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import live.omnisource.tessera.catalog.entity.CatalogEntry;
import live.omnisource.tessera.filestore.FileStoreLayout;
import live.omnisource.tessera.filestore.FileStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * File-based catalog store. Each query is stored as a JSON file under
 * {@code data_dir/etc/catalog/queries/{name}.json}.
 *
 * <p>The file is the source of truth. On first boot, built-in seed queries
 * are copied from the classpath into the directory. The service maintains
 * an in-memory cache that is refreshed on read operations.</p>
 *
 * <p>File format matches the CatalogEntry fields (minus JPA-specific ones
 * like the UUID id, which is derived from the name).</p>
 */
@Slf4j
@Component
public class FileCatalogStore {

    private static final String SEED_CLASSPATH = "classpath:catalog/seeds/*.json";

    private final FileStoreService fileStore;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, CatalogEntry> cache = new ConcurrentHashMap<>();
    private volatile Instant lastScan = Instant.EPOCH;

    public FileCatalogStore(FileStoreService fileStore, ObjectMapper objectMapper) {
        this.fileStore = fileStore;
        this.objectMapper = objectMapper;
    }

    // ── Bootstrap ───────────────────────────────────────

    /**
     * Seed built-in queries from classpath if the queries directory is empty.
     * Called during startup reconciliation.
     */
    public void seedIfEmpty() {
        Path dir = queriesDir();
        try {
            if (Files.isDirectory(dir)) {
                try (Stream<Path> s = Files.list(dir)) {
                    if (s.anyMatch(p -> p.toString().endsWith(".json"))) {
                        log.debug("Catalog directory already has queries, skipping seed");
                        return;
                    }
                }
            }

            log.info("Seeding built-in catalog queries from classpath");
            var resolver = new PathMatchingResourcePatternResolver();
            Resource[] seeds = resolver.getResources(SEED_CLASSPATH);

            for (Resource seed : seeds) {
                String filename = seed.getFilename();
                if (filename == null) continue;

                byte[] content = seed.getInputStream().readAllBytes();
                Path target = dir.resolve(filename);
                fileStore.writeAtomic(target, content);
                log.info("  Seeded: {}", filename);
            }
        } catch (IOException e) {
            log.warn("Failed to seed catalog queries: {}", e.getMessage());
        }
    }

    // ── Read ────────────────────────────────────────────

    public List<CatalogEntry> listAll() {
        refreshCache();
        return cache.values().stream()
                .sorted(Comparator.comparing(CatalogEntry::getCategory)
                        .thenComparing(CatalogEntry::getName))
                .toList();
    }

    public List<CatalogEntry> listByCategory(String category) {
        refreshCache();
        return cache.values().stream()
                .filter(e -> e.getCategory().equalsIgnoreCase(category))
                .sorted(Comparator.comparing(CatalogEntry::getName))
                .toList();
    }

    public Optional<CatalogEntry> findByName(String name) {
        refreshCache();
        return Optional.ofNullable(cache.get(name));
    }

    public boolean existsByName(String name) {
        refreshCache();
        return cache.containsKey(name);
    }

    // ── Write ───────────────────────────────────────────

    public CatalogEntry save(CatalogEntry entry) {
        Path file = queriesDir().resolve(entry.getName() + ".json");
        try {
            if (entry.getCreatedAt() == null) entry.setCreatedAt(Instant.now());
            entry.setUpdatedAt(Instant.now());
            if (entry.getId() == null) entry.setId(UUID.nameUUIDFromBytes(entry.getName().getBytes()));

            byte[] json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(entry);
            fileStore.writeAtomic(file, json);
            cache.put(entry.getName(), entry);
            log.debug("Saved catalog entry to file: {}", entry.getName());
            return entry;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write catalog entry: " + entry.getName(), e);
        }
    }

    public void deleteByName(String name) {
        Path file = queriesDir().resolve(name + ".json");
        try {
            Files.deleteIfExists(file);
            cache.remove(name);
            log.info("Deleted catalog entry file: {}", name);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete catalog entry: " + name, e);
        }
    }

    // ── Cache management ────────────────────────────────

    /**
     * Reload all entries from disk. Called periodically and on write.
     */
    public void refreshCache() {
        Path dir = queriesDir();
        if (!Files.isDirectory(dir)) return;

        try (Stream<Path> files = Files.list(dir)) {
            var newCache = new HashMap<String, CatalogEntry>();

            files.filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            byte[] bytes = Files.readAllBytes(p);
                            CatalogEntry entry = objectMapper.readValue(bytes, CatalogEntry.class);

                            // Derive ID from name if not present
                            if (entry.getId() == null) {
                                entry.setId(UUID.nameUUIDFromBytes(entry.getName().getBytes()));
                            }
                            if (entry.getCreatedAt() == null) entry.setCreatedAt(Instant.now());
                            if (entry.getUpdatedAt() == null) entry.setUpdatedAt(Instant.now());

                            newCache.put(entry.getName(), entry);
                        } catch (IOException e) {
                            log.warn("Failed to read catalog file {}: {}", p.getFileName(), e.getMessage());
                        }
                    });

            cache.clear();
            cache.putAll(newCache);
            lastScan = Instant.now();
            log.debug("Catalog cache refreshed: {} entries", cache.size());
        } catch (IOException e) {
            log.warn("Failed to scan catalog directory: {}", e.getMessage());
        }
    }

    /**
     * Return all cached entries for sync to DB.
     */
    public Collection<CatalogEntry> allCached() {
        refreshCache();
        return Collections.unmodifiableCollection(cache.values());
    }

    // ── Helpers ─────────────────────────────────────────

    private Path queriesDir() {
        return fileStore.resolve(FileStoreLayout.QUERIES);
    }
}