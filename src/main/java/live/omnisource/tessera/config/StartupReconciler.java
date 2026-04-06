package live.omnisource.tessera.config;

import live.omnisource.tessera.catalog.FileCatalogStore;
import live.omnisource.tessera.catalog.entity.CatalogEntry;
import live.omnisource.tessera.catalog.repository.CatalogRepository;
import live.omnisource.tessera.filestore.FileStoreLayout;
import live.omnisource.tessera.filestore.FileStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Runs on application startup to ensure consistency between the file-based
 * data_dir (source of truth) and the database (execution engine).
 *
 * <h3>Reconciliation steps:</h3>
 * <ol>
 *   <li><b>Catalog seed</b> — if {@code data_dir/etc/catalog/queries/} is empty,
 *       copies built-in query definitions from the classpath</li>
 *   <li><b>Catalog sync</b> — upserts all file-based catalog entries into the
 *       {@code query_catalog} DB table so the QueryExecutor can use them</li>
 *   <li><b>Layer reconciliation</b> — scans data_dir for workspace/datastore/layer
 *       paths, compares against {@code external_sources} rows, and purges
 *       orphans (DB records whose layer directory no longer exists)</li>
 * </ol>
 *
 * <p>The data_dir is always authoritative. If a layer, datastore, or workspace
 * directory has been removed, all associated DB records are cleaned up.</p>
 */
@Slf4j
@Component
public class StartupReconciler {

    private static final String LAYER_METADATA = "layer.json";

    private final FileCatalogStore fileCatalogStore;
    private final CatalogRepository catalogRepository;
    private final FileStoreService fileStore;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate txTemplate;

    public StartupReconciler(FileCatalogStore fileCatalogStore,
                             CatalogRepository catalogRepository,
                             FileStoreService fileStore,
                             JdbcTemplate jdbc,
                             TransactionTemplate txTemplate) {
        this.fileCatalogStore = fileCatalogStore;
        this.catalogRepository = catalogRepository;
        this.fileStore = fileStore;
        this.jdbc = jdbc;
        this.txTemplate = txTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() {
        log.info("╔══════════════════════════════════════════╗");
        log.info("║  Startup Reconciliation                  ║");
        log.info("╚══════════════════════════════════════════╝");

        try {
            seedCatalog();
            syncCatalogToDb();
            reconcileLayers();
            log.info("Startup reconciliation complete");
        } catch (Exception e) {
            log.error("Startup reconciliation failed — server will continue but data may be inconsistent", e);
        }
    }

    // ── 1. Seed catalog ─────────────────────────────────

    private void seedCatalog() {
        log.info("Step 1: Checking catalog seed…");
        fileCatalogStore.seedIfEmpty();
    }

    // ── 2. Sync file catalog → DB ───────────────────────

    private void syncCatalogToDb() {
        log.info("Step 2: Syncing file catalog → DB…");
        var fileEntries = fileCatalogStore.allCached();

        if (fileEntries.isEmpty()) {
            log.info("  No catalog entries in data_dir — skipping DB sync");
            return;
        }

        int created = 0, updated = 0, skipped = 0;

        for (CatalogEntry fileEntry : fileEntries) {
            try {
                var dbEntry = catalogRepository.findByName(fileEntry.getName());

                if (dbEntry.isEmpty()) {
                    // New entry — insert into DB
                    var entity = new CatalogEntry();
                    copyFields(fileEntry, entity);
                    catalogRepository.save(entity);
                    created++;
                } else {
                    // Existing — update if file is newer
                    var existing = dbEntry.get();
                    if (fileEntry.getUpdatedAt() != null
                            && existing.getUpdatedAt() != null
                            && fileEntry.getUpdatedAt().isAfter(existing.getUpdatedAt())) {
                        copyFields(fileEntry, existing);
                        catalogRepository.save(existing);
                        updated++;
                    } else {
                        skipped++;
                    }
                }
            } catch (Exception e) {
                log.warn("  Failed to sync catalog entry '{}': {}", fileEntry.getName(), e.getMessage());
            }
        }

        // Purge DB entries that don't exist in files
        int purged = 0;
        var allDbEntries = catalogRepository.findAll();
        var fileNames = fileEntries.stream().map(CatalogEntry::getName).collect(java.util.stream.Collectors.toSet());
        for (var dbEntry : allDbEntries) {
            if (!fileNames.contains(dbEntry.getName())) {
                catalogRepository.delete(dbEntry);
                purged++;
            }
        }

        log.info("  Catalog sync: {} created, {} updated, {} skipped, {} purged from DB",
                created, updated, skipped, purged);
    }

    // ── 3. Layer reconciliation ─────────────────────────

    private void reconcileLayers() {
        log.info("Step 3: Reconciling layers vs external_sources…");

        // Discover all layer paths from data_dir
        Set<String> dataDirLayers = discoverLayerPaths();
        log.info("  Found {} layer(s) in data_dir", dataDirLayers.size());

        // Load all external_sources from DB
        List<Map<String, Object>> dbSources = jdbc.queryForList(
                "SELECT id, name FROM tessera.external_sources");
        log.info("  Found {} external_source(s) in DB", dbSources.size());

        // Find orphans: DB records whose layer path no longer exists in data_dir
        List<UUID> orphanIds = new ArrayList<>();
        List<String> orphanNames = new ArrayList<>();

        for (var row : dbSources) {
            String sourceName = (String) row.get("name");
            UUID sourceId = (UUID) row.get("id");

            // Skip the demo generator source
            if (sourceName != null && sourceName.startsWith("_")) continue;

            if (!dataDirLayers.contains(sourceName)) {
                orphanIds.add(sourceId);
                orphanNames.add(sourceName);
            }
        }

        if (orphanIds.isEmpty()) {
            log.info("  No orphan records found — DB is consistent with data_dir");
            return;
        }

        log.warn("  Found {} orphan external_source(s) to purge: {}", orphanIds.size(), orphanNames);

        // Purge in a single transaction
        txTemplate.executeWithoutResult(status -> {
            for (UUID id : orphanIds) {
                purgeExternalSource(id);
            }
        });

        log.info("  Purged {} orphan external_source(s) and associated data", orphanIds.size());
    }

    /**
     * Scan data_dir to find all workspace/datastore/layer paths that have a layer.json.
     * Returns a set of source names in the format "workspace/datastore/layer".
     */
    private Set<String> discoverLayerPaths() {
        Path workspacesRoot = fileStore.resolve(FileStoreLayout.WORKSPACES);
        Set<String> paths = new LinkedHashSet<>();

        if (!Files.isDirectory(workspacesRoot)) return paths;

        try (Stream<Path> workspaces = Files.list(workspacesRoot)) {
            workspaces.filter(Files::isDirectory).forEach(wsDir -> {
                String workspace = wsDir.getFileName().toString();

                // Each workspace has a 'data' subdirectory containing datastores
                // (from WorkspaceService.createWorkspaceDirectories)
                Path dataDir = wsDir.resolve("data");
                if (!Files.isDirectory(dataDir)) {
                    // Also check direct children (older layout)
                    dataDir = wsDir;
                }

                try (Stream<Path> datastores = Files.list(dataDir)) {
                    datastores.filter(Files::isDirectory).forEach(dsDir -> {
                        String datastore = dsDir.getFileName().toString();
                        if (datastore.equals("data")) return; // skip the 'data' dir itself

                        // Layers are subdirectories with layer.json
                        Path layersDir = dsDir.resolve("layers");
                        if (!Files.isDirectory(layersDir)) layersDir = dsDir;

                        try (Stream<Path> layers = Files.list(layersDir)) {
                            layers.filter(Files::isDirectory).forEach(layerDir -> {
                                String layer = layerDir.getFileName().toString();
                                if (Files.exists(layerDir.resolve(LAYER_METADATA))) {
                                    paths.add(workspace + "/" + datastore + "/" + layer);
                                }
                            });
                        } catch (IOException ignored) {}
                    });
                } catch (IOException ignored) {}
            });
        } catch (IOException e) {
            log.warn("Failed to scan workspaces directory: {}", e.getMessage());
        }

        return paths;
    }

    /**
     * Purge all DB records associated with an external_source:
     * geo_features (no FK, manual delete), sync_checkpoints (FK no cascade),
     * then the external_source itself (cascades schema_metadata).
     */
    private void purgeExternalSource(UUID sourceId) {
        String id = sourceId.toString();

        // 1. Delete geo_features (partitioned, no FK)
        int features = jdbc.update(
                "DELETE FROM tessera.geo_features WHERE source_id = ?::uuid", id);

        // 2. Delete h3_cell_index entries for those features
        // (These reference feature_id, but since features are gone, orphan h3 entries
        //  will be caught by the next h3 cleanup. For now, skip — h3 has no FK to sources.)

        // 3. Delete sync_checkpoints (FK to external_sources, no cascade)
        int checkpoints = jdbc.update(
                "DELETE FROM tessera.sync_checkpoints WHERE source_id = ?::uuid", id);

        // 4. Delete external_source (cascades schema_metadata)
        int sources = jdbc.update(
                "DELETE FROM tessera.external_sources WHERE id = ?::uuid", id);

        log.info("    Purged source {}: {} features, {} checkpoints, {} source row",
                id, features, checkpoints, sources);
    }

    // ── Helpers ─────────────────────────────────────────

    private void copyFields(CatalogEntry from, CatalogEntry to) {
        to.setName(from.getName());
        to.setDescription(from.getDescription());
        to.setCategory(from.getCategory());
        to.setQuerySql(from.getQuerySql());
        to.setParamSchema(from.getParamSchema());
        to.setResultSchema(from.getResultSchema());
        to.setTimeoutMs(from.getTimeoutMs());
        to.setStreaming(from.isStreaming());
        to.setCacheTtlSec(from.getCacheTtlSec());
        to.setTags(from.getTags());
    }
}