package live.omnisource.tessera.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import live.omnisource.tessera.filestore.FileStoreLayout;
import live.omnisource.tessera.filestore.FileStoreService;
import live.omnisource.tessera.layer.dto.LayerRecord;
import live.omnisource.tessera.sync.dto.SyncJobResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

/**
 * Periodically scans all layers and dispatches incremental syncs
 * for those with auto-sync enabled whose poll interval has elapsed.
 *
 * Runs on a fixed 30-second tick. Each tick:
 *   1. Walk every workspace → datastore → layer
 *   2. Read layer.json for SyncConfig
 *   3. Read sync.json for last sync time
 *   4. If enabled and interval has elapsed → dispatch sync
 *
 * Uses virtual threads (via the taskExecutor bean) for dispatch,
 * and tracks in-flight syncs to prevent overlapping runs on the same layer.
 */
@Slf4j
@Component
public class SyncScheduler {

    private final FileStoreService fileStoreService;
    private final FeatureSyncService syncService;
    private final ObjectMapper objectMapper;
    private final Executor taskExecutor;

    /** Tracks layers currently being synced to prevent overlap. */
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public SyncScheduler(FileStoreService fileStoreService,
                         FeatureSyncService syncService,
                         ObjectMapper objectMapper,
                         Executor taskExecutor) {
        this.fileStoreService = fileStoreService;
        this.syncService = syncService;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Fixed-delay tick: scans all layers every 30 seconds.
     * Initial delay of 60s lets the app finish booting before the first scan.
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 60_000)
    public void tick() {
        Path workspacesRoot = fileStoreService.resolve(FileStoreLayout.WORKSPACES);
        if (!Files.isDirectory(workspacesRoot)) return;

        try (Stream<Path> workspaces = Files.list(workspacesRoot)) {
            workspaces.filter(Files::isDirectory).forEach(this::scanWorkspace);
        } catch (IOException e) {
            log.warn("Scheduler scan failed: {}", e.getMessage());
        }
    }

    private void scanWorkspace(Path wsDir) {
        String workspace = wsDir.getFileName().toString();
        Path dataDir = wsDir.resolve("data");
        if (!Files.isDirectory(dataDir)) return;

        try (Stream<Path> stores = Files.list(dataDir)) {
            stores.filter(Files::isDirectory)
                    .forEach(ds -> scanDataStore(workspace, ds));
        } catch (IOException e) {
            log.warn("Error scanning workspace {}: {}", workspace, e.getMessage());
        }
    }

    private void scanDataStore(String workspace, Path dsDir) {
        String datastore = dsDir.getFileName().toString();
        Path layersDir = dsDir.resolve("layers");
        if (!Files.isDirectory(layersDir)) return;

        try (Stream<Path> layers = Files.list(layersDir)) {
            layers.filter(Files::isDirectory)
                    .forEach(l -> evaluateLayer(workspace, datastore, l));
        } catch (IOException e) {
            log.warn("Error scanning datastore {}/{}: {}", workspace, datastore, e.getMessage());
        }
    }

    private void evaluateLayer(String workspace, String datastore, Path layerDir) {
        String layerName = layerDir.getFileName().toString();
        String layerKey = workspace + "/" + datastore + "/" + layerName;

        try {
            Path layerFile = layerDir.resolve("layer.json");
            if (!Files.exists(layerFile)) return;

            LayerRecord record = objectMapper.readValue(
                    Files.readAllBytes(layerFile), LayerRecord.class);

            // Must be active with sync enabled
            if (record.syncConfig() == null || !record.syncConfig().enabled()) return;
            if (!"ACTIVE".equals(record.status())) return;

            // Check if poll interval has elapsed since last sync
            Instant lastSync = readLastSyncTime(layerDir);
            int intervalSeconds = Math.max(record.syncConfig().pollIntervalSeconds(), 30);

            if (lastSync != null &&
                    Duration.between(lastSync, Instant.now()).toSeconds() < intervalSeconds) {
                return; // not due yet
            }

            // Prevent overlapping syncs on the same layer
            if (!inFlight.add(layerKey)) {
                log.trace("Skipping {} — sync already in flight", layerKey);
                return;
            }

            log.info("Dispatching auto-sync for {} (last sync: {})", layerKey,
                    lastSync != null ? lastSync : "never");

            taskExecutor.execute(() -> {
                try {
                    SyncJobResult result = syncService.syncLayer(workspace, datastore, layerName);
                    if ("FAILED".equals(result.status())) {
                        log.warn("Auto-sync failed for {}: {}", layerKey, result.errorMessage());
                    } else {
                        log.info("Auto-sync completed for {}: {} features in {}s",
                                layerKey, result.featuresWritten(),
                                result.duration().toSeconds());
                    }
                } catch (Exception e) {
                    log.error("Unexpected error during auto-sync of {}: {}",
                            layerKey, e.getMessage());
                } finally {
                    inFlight.remove(layerKey);
                }
            });

        } catch (IOException e) {
            log.warn("Error evaluating layer {}: {}", layerKey, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Instant readLastSyncTime(Path layerDir) {
        Path syncFile = layerDir.resolve("sync.json");
        if (!Files.exists(syncFile)) return null;
        try {
            Map<String, Object> meta = objectMapper.readValue(
                    Files.readAllBytes(syncFile), Map.class);
            String lastSync = (String) meta.get("lastSync");
            return lastSync != null ? Instant.parse(lastSync) : null;
        } catch (Exception e) {
            return null;
        }
    }
}