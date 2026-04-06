package live.omnisource.tessera.feed;

import com.fasterxml.jackson.databind.ObjectMapper;
import live.omnisource.tessera.datastore.dto.DataStoreDto;
import live.omnisource.tessera.datastore.dto.DataStoreRecord;
import live.omnisource.tessera.feed.connector.*;
import live.omnisource.tessera.feed.dto.FeedConfig;
import live.omnisource.tessera.feed.dto.FeedConfig.FeedType;
import live.omnisource.tessera.feed.dto.FeedConfig.GeometryType;
import live.omnisource.tessera.feed.dto.FeedStatus;
import live.omnisource.tessera.filestore.FileStoreLayout;
import live.omnisource.tessera.filestore.FileStoreService;
import live.omnisource.tessera.layer.dto.LayerDto;
import live.omnisource.tessera.layer.dto.LayerRecord;
import live.omnisource.tessera.sync.FeatureBatchWriter;
import live.omnisource.tessera.sync.dto.ExtractedFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Manages workspace-scoped live feed lifecycle.
 *
 * <p>Feeds are stored under {@code data_dir/etc/catalog/workspaces/{workspace}/feeds/{name}/feed.json}.
 * Each feed belongs to exactly one workspace and inherits workspace RBAC.</p>
 *
 * <p>On startup, scans all workspaces for enabled feeds and auto-starts them.</p>
 */
@Slf4j
@Service
public class FeedService {

    private static final String FEEDS_DIR = "feeds";
    private static final String FEED_FILE = "feed.json";
    private static final String FEED_DATASTORE = "feed";  // virtual datastore name for feed layers

    private final FileStoreService fileStore;
    private final ObjectMapper objectMapper;
    private final FeedNormalizer normalizer;
    private final FeatureBatchWriter batchWriter;
    private final JdbcTemplate jdbc;

    /** Active feed runtimes keyed by "workspace/feedName" */
    private final ConcurrentHashMap<String, FeedRuntime> runtimes = new ConcurrentHashMap<>();

    public FeedService(FileStoreService fileStore,
                       ObjectMapper objectMapper,
                       FeedNormalizer normalizer,
                       FeatureBatchWriter batchWriter,
                       JdbcTemplate jdbc) {
        this.fileStore = fileStore;
        this.objectMapper = objectMapper;
        this.normalizer = normalizer;
        this.batchWriter = batchWriter;
        this.jdbc = jdbc;
    }

    // ── Startup / Shutdown ──────────────────────────────

    @PostConstruct
    public void autoStart() {
        int started = 0;
        int total = 0;
        for (String workspace : listWorkspaces()) {
            for (FeedConfig config : listFeeds(workspace)) {
                total++;
                if (config.isEnabled()) {
                    try {
                        startFeed(workspace, config.name());
                        started++;
                    } catch (Exception e) {
                        log.warn("Failed to auto-start feed '{}/{}': {}", workspace, config.name(), e.getMessage());
                    }
                }
            }
        }
        log.info("Feed service initialized: {} feeds found, {} auto-started", total, started);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down {} active feeds", runtimes.size());
        new ArrayList<>(runtimes.keySet()).forEach(key -> {
            String[] parts = key.split("/", 2);
            if (parts.length == 2) stopFeed(parts[0], parts[1]);
        });
    }

    // ── CRUD ────────────────────────────────────────────

    public List<FeedConfig> listFeeds(String workspace) {
        Path dir = feedsDir(workspace);
        if (!Files.isDirectory(dir)) return List.of();

        try (Stream<Path> subdirs = Files.list(dir)) {
            return subdirs.filter(Files::isDirectory)
                    .map(d -> d.resolve(FEED_FILE))
                    .filter(Files::exists)
                    .map(this::readConfig)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(FeedConfig::name))
                    .toList();
        } catch (IOException e) {
            log.warn("Failed to list feeds for workspace {}: {}", workspace, e.getMessage());
            return List.of();
        }
    }

    public Optional<FeedConfig> getFeed(String workspace, String name) {
        Path file = feedDir(workspace, name).resolve(FEED_FILE);
        if (!Files.exists(file)) return Optional.empty();
        return Optional.ofNullable(readConfig(file));
    }

    public FeedConfig createFeed(String workspace, FeedConfig config) {
        validateName(config.name());
        Path dir = feedDir(workspace, config.name());
        if (Files.exists(dir)) {
            throw new IllegalArgumentException("Feed already exists: " + config.name());
        }

        var withTimestamps = new FeedConfig(
                config.name(), config.type(), config.isEnabled(),
                config.connection(), config.schema(), config.ingest(),
                Instant.now(), Instant.now()
        );

        writeConfig(workspace, withTimestamps);
        provisionFeedLayer(workspace, withTimestamps);
        log.info("Created feed: {}/{} (type={})", workspace, config.name(), config.type());
        return withTimestamps;
    }

    public FeedConfig updateFeed(String workspace, String name, FeedConfig config) {
        var existing = getFeed(workspace, name).orElseThrow(
                () -> new IllegalArgumentException("Feed not found: " + workspace + "/" + name));

        if (runtimes.containsKey(runtimeKey(workspace, name))) {
            stopFeed(workspace, name);
        }

        var updated = new FeedConfig(
                name, config.type(), config.isEnabled(),
                config.connection(), config.schema(), config.ingest(),
                existing.createdAt(), Instant.now()
        );

        writeConfig(workspace, updated);
        log.info("Updated feed: {}/{}", workspace, name);

        if (updated.isEnabled()) {
            startFeed(workspace, name);
        }
        return updated;
    }

    public void deleteFeed(String workspace, String name) {
        stopFeed(workspace, name);

        // Remove feed config
        Path dir = feedDir(workspace, name);
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete feed: " + workspace + "/" + name, e);
            }
        }

        // Clean up auto-provisioned layer
        deprovisionFeedLayer(workspace, name);
        log.info("Deleted feed: {}/{}", workspace, name);
    }

    // ── Lifecycle ───────────────────────────────────────

    public void startFeed(String workspace, String name) {
        String key = runtimeKey(workspace, name);
        if (runtimes.containsKey(key)) {
            log.info("Feed '{}/{}' is already running", workspace, name);
            return;
        }

        var config = getFeed(workspace, name).orElseThrow(
                () -> new IllegalArgumentException("Feed not found: " + workspace + "/" + name));

        FeedConnector connector = createConnector(config.type());
        var runtime = new FeedRuntime(workspace, config, connector);
        runtimes.put(key, runtime);

        connector.start(config, message -> processMessage(runtime, message));
        log.info("Feed started: {}/{} (type={})", workspace, name, config.type());
    }

    public void stopFeed(String workspace, String name) {
        String key = runtimeKey(workspace, name);
        var runtime = runtimes.remove(key);
        if (runtime != null) {
            runtime.connector.stop();
            log.info("Feed stopped: {}/{} (received={}, ingested={}, errors={})",
                    workspace, name, runtime.messagesReceived.get(),
                    runtime.featuresIngested.get(), runtime.errors.get());
        }
    }

    public FeedStatus getStatus(String workspace, String name) {
        String key = runtimeKey(workspace, name);
        var runtime = runtimes.get(key);
        if (runtime != null) {
            return new FeedStatus(
                    name, runtime.config.type(), runtime.connector.state(),
                    runtime.messagesReceived.get(), runtime.featuresIngested.get(),
                    runtime.errors.get(), runtime.startedAt,
                    runtime.lastMessageAt, runtime.lastError
            );
        }
        return getFeed(workspace, name)
                .map(FeedStatus::stopped)
                .orElse(null);
    }

    public List<FeedStatus> allStatuses(String workspace) {
        var statuses = new ArrayList<FeedStatus>();
        for (FeedConfig config : listFeeds(workspace)) {
            var s = getStatus(workspace, config.name());
            if (s != null) statuses.add(s);
        }
        return statuses;
    }

    public int activeFeedCount(String workspace) {
        String prefix = workspace + "/";
        return (int) runtimes.keySet().stream().filter(k -> k.startsWith(prefix)).count();
    }

    public int totalActiveFeedCount() {
        return runtimes.size();
    }

    public String testFeed(String workspace, String name) throws Exception {
        var config = getFeed(workspace, name).orElseThrow(
                () -> new IllegalArgumentException("Feed not found: " + workspace + "/" + name));
        FeedConnector connector = createConnector(config.type());
        return connector.testConnection(config);
    }

    // ── Message processing pipeline ─────────────────────

    private void processMessage(FeedRuntime runtime, String message) {
        long msgCount = runtime.messagesReceived.incrementAndGet();
        runtime.lastMessageAt = Instant.now();

        // Log first message and then every 100th for diagnostics
        boolean logDiag = (msgCount == 1 || msgCount % 100 == 0);

        try {
            var features = normalizer.normalize(message, runtime.config.schema());

            if (features.isEmpty()) {
                if (msgCount <= 5) {
                    // Log the first 5 empty results at WARN with a message snippet
                    log.warn("Feed {}/{} msg#{}: normalizer returned 0 features. Message preview: {}",
                            runtime.workspace, runtime.config.name(), msgCount,
                            message.length() > 300 ? message.substring(0, 300) + "…" : message);
                } else if (logDiag) {
                    log.info("Feed {}/{} msg#{}: normalizer returned 0 features (received {} total, ingested {})",
                            runtime.workspace, runtime.config.name(), msgCount,
                            runtime.messagesReceived.get(), runtime.featuresIngested.get());
                }
                return;
            }

            var ingest = runtime.config.ingest();
            String sourceTable = ingest != null && ingest.sourceTable() != null
                    ? ingest.sourceTable() : "feed." + runtime.config.name();

            UUID sourceId = ensureExternalSource(runtime);

            int[] h3Res = ingest != null && ingest.isH3Index()
                    ? ingest.resolvedH3Resolutions() : new int[0];

            int batchSize = ingest != null ? ingest.resolvedBatchSize() : 100;
            var batch = new ArrayList<ExtractedFeature>(batchSize);

            int nullGeomSkipped = 0;
            for (var feature : features) {
                if (feature.geometry() != null) {
                    batch.add(feature);
                } else {
                    nullGeomSkipped++;
                }
                if (batch.size() >= batchSize) {
                    batchWriter.writeBatch(sourceId, sourceTable, batch, h3Res);
                    runtime.featuresIngested.addAndGet(batch.size());
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                batchWriter.writeBatch(sourceId, sourceTable, batch, h3Res);
                runtime.featuresIngested.addAndGet(batch.size());
            }

            if (nullGeomSkipped > 0 && msgCount <= 10) {
                log.warn("Feed {}/{} msg#{}: {} features extracted, {} skipped (null geometry)",
                        runtime.workspace, runtime.config.name(), msgCount,
                        features.size() - nullGeomSkipped, nullGeomSkipped);
            }

            if (logDiag) {
                log.info("Feed {}/{}: {} messages received, {} features ingested, {} errors",
                        runtime.workspace, runtime.config.name(),
                        runtime.messagesReceived.get(), runtime.featuresIngested.get(),
                        runtime.errors.get());
            }

        } catch (Exception e) {
            runtime.errors.incrementAndGet();
            runtime.lastError = e.getMessage();
            if (msgCount <= 5) {
                log.warn("Feed {}/{} msg#{} processing error: {}", runtime.workspace,
                        runtime.config.name(), msgCount, e.getMessage(), e);
            } else if (logDiag) {
                log.warn("Feed {}/{} msg#{} processing error: {}", runtime.workspace,
                        runtime.config.name(), msgCount, e.getMessage());
            }
        }
    }

    private UUID ensureExternalSource(FeedRuntime runtime) {
        if (runtime.sourceId != null) return runtime.sourceId;

        String sourceName = runtime.workspace + "/" + FEED_DATASTORE + "/" + runtime.config.name();
        var existing = jdbc.queryForList(
                "SELECT id FROM tessera.external_sources WHERE name = ?", sourceName);

        if (!existing.isEmpty()) {
            runtime.sourceId = (UUID) existing.getFirst().get("id");
            return runtime.sourceId;
        }

        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO tessera.external_sources (id, name, source_type, connection_key, is_active, sync_strategy)
            VALUES (?::uuid, ?, 'POSTGIS', ?, true, 'BATCH')
            """, id.toString(), sourceName, "feed/" + runtime.workspace + "/" + runtime.config.name());

        runtime.sourceId = id;
        log.info("Created external source for feed: {}/{} → {}", runtime.workspace, runtime.config.name(), id);
        return id;
    }

    // ── Layer provisioning ──────────────────────────────

    /**
     * Auto-provision a layer and external source when a feed is created.
     * Creates a virtual "feed" datastore in the workspace, registers the
     * layer metadata, and ensures an external_sources row exists so the
     * map and layer groups can query features immediately once data arrives.
     */
    private void provisionFeedLayer(String workspace, FeedConfig config) {
        try {
            // 1. Ensure "feed" virtual datastore directory + metadata
            Path wsDataDir = fileStore.resolve(FileStoreLayout.WORKSPACES)
                    .resolve(workspace).resolve("data").resolve(FEED_DATASTORE);
            Path dsFile = wsDataDir.resolve("datastore.json");

            if (!Files.exists(dsFile)) {
                Files.createDirectories(wsDataDir);
                var dsRecord = new DataStoreRecord(
                        new DataStoreDto(workspace, FEED_DATASTORE),
                        "FEED", null, 0, null, 0, null, null, null, false
                );
                fileStore.writeAtomic(dsFile, objectMapper.writeValueAsBytes(dsRecord));
                log.info("Created '{}' datastore for workspace: {}", FEED_DATASTORE, workspace);
            }

            // 2. Create layer metadata
            String feedName = config.name();
            Path layerDir = wsDataDir.resolve("layers").resolve(feedName);
            Path layerFile = layerDir.resolve("layer.json");

            if (!Files.exists(layerFile)) {
                Files.createDirectories(layerDir);

                // Derive source table from ingest config
                String sourceTable = (config.ingest() != null && config.ingest().sourceTable() != null)
                        ? config.ingest().sourceTable() : "feed." + feedName;

                String[] parts = sourceTable.contains(".")
                        ? sourceTable.split("\\.", 2)
                        : new String[]{"feed", sourceTable};

                // Derive geometry info from schema
                var schema = config.schema();
                int srid = (schema != null && schema.geometry() != null)
                        ? schema.geometry().resolvedSrid() : 4326;
                String geomType = (schema != null && schema.geometry() != null && schema.geometry().type() != null)
                        ? mapGeometryType(schema.geometry().type()) : "Point";

                var layerRecord = new LayerRecord(
                        new LayerDto(workspace, FEED_DATASTORE, feedName),
                        parts[0],                            // sourceSchema
                        parts[1],                            // sourceTable
                        "geometry",                          // geometryColumn
                        geomType,                            // geometryType
                        srid,                                // srid
                        0L,                                  // rowCount (none yet)
                        new double[]{-180, -90, 180, 90},    // extent (global — feed data can arrive from anywhere)
                        "ACTIVE",                            // status
                        null                                 // syncConfig (feeds use connectors, not sync scheduler)
                );
                fileStore.writeAtomic(layerFile, objectMapper.writeValueAsBytes(layerRecord));
                log.info("Created layer: {}/{}/{}", workspace, FEED_DATASTORE, feedName);
            }

            // 3. Register external source in DB (so map + layer groups can resolve it)
            String sourceName = workspace + "/" + FEED_DATASTORE + "/" + feedName;
            var existing = jdbc.queryForList(
                    "SELECT id FROM tessera.external_sources WHERE name = ?", sourceName);

            if (existing.isEmpty()) {
                UUID id = UUID.randomUUID();
                jdbc.update("""
                    INSERT INTO tessera.external_sources (id, name, source_type, connection_key, is_active, sync_strategy)
                    VALUES (?::uuid, ?, 'POSTGIS', ?, true, 'BATCH')
                    """, id.toString(), sourceName, "feed/" + workspace + "/" + feedName);
                log.info("Registered external source for feed: {} → {}", sourceName, id);
            }

        } catch (Exception e) {
            // Non-fatal — feed is still created, layer can be provisioned later on first message
            log.warn("Failed to provision layer for feed {}/{}: {}", workspace, config.name(), e.getMessage());
        }
    }

    private static String mapGeometryType(GeometryType type) {
        if (type == null) return "Point";
        return switch (type) {
            case LAT_LNG, H3, MGRS -> "Point";
            case WKT, WKB, GEOJSON -> "Geometry";
            case NONE -> "Geometry";
        };
    }

    /**
     * Remove the auto-provisioned layer and external_source when a feed is deleted.
     */
    private void deprovisionFeedLayer(String workspace, String name) {
        try {
            // 1. Remove layer directory
            Path layerDir = fileStore.resolve(FileStoreLayout.WORKSPACES)
                    .resolve(workspace).resolve("data").resolve(FEED_DATASTORE)
                    .resolve("layers").resolve(name);

            if (Files.exists(layerDir)) {
                try (Stream<Path> walk = Files.walk(layerDir)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
                }
                log.info("Removed feed layer: {}/{}/{}", workspace, FEED_DATASTORE, name);
            }

            // 2. Remove external_source + cascade geo_features
            String sourceName = workspace + "/" + FEED_DATASTORE + "/" + name;
            var rows = jdbc.queryForList(
                    "SELECT id FROM tessera.external_sources WHERE name = ?", sourceName);

            for (var row : rows) {
                UUID sourceId = (UUID) row.get("id");
                String sid = sourceId.toString();
                // Delete h3 first (references feature_id from geo_features)
                jdbc.update("DELETE FROM tessera.h3_cell_index WHERE feature_id IN " +
                        "(SELECT id FROM tessera.geo_features WHERE source_id = ?::uuid)", sid);
                jdbc.update("DELETE FROM tessera.geo_features WHERE source_id = ?::uuid", sid);
                jdbc.update("DELETE FROM tessera.sync_checkpoints WHERE source_id = ?::uuid", sid);
                jdbc.update("DELETE FROM tessera.external_sources WHERE id = ?::uuid", sid);
                log.info("Purged external source for feed: {} → {}", sourceName, sourceId);
            }

        } catch (Exception e) {
            log.warn("Failed to deprovision layer for feed {}/{}: {}", workspace, name, e.getMessage());
        }
    }

    // ── Factory ─────────────────────────────────────────

    private FeedConnector createConnector(FeedType type) {
        return switch (type) {
            case REST_POLL -> new RestPollFeedConnector();
            case TCP -> new TcpFeedConnector();
            case WEBSOCKET -> new WebSocketFeedConnector();
            case KAFKA -> new KafkaFeedConnector();
            case AMQP -> new AmqpFeedConnector();
        };
    }

    // ── File I/O ────────────────────────────────────────

    private FeedConfig readConfig(Path file) {
        try {
            return objectMapper.readValue(Files.readAllBytes(file), FeedConfig.class);
        } catch (IOException e) {
            log.warn("Failed to read feed config {}: {}", file, e.getMessage());
            return null;
        }
    }

    private void writeConfig(String workspace, FeedConfig config) {
        try {
            Path dir = feedDir(workspace, config.name());
            Files.createDirectories(dir);
            byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(config);
            fileStore.writeAtomic(dir.resolve(FEED_FILE), json);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write feed config: " + workspace + "/" + config.name(), e);
        }
    }

    private void validateName(String name) {
        if (name == null || !name.matches("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$")) {
            throw new IllegalArgumentException(
                    "Invalid feed name. Use [a-zA-Z0-9][a-zA-Z0-9_-]{0,63}");
        }
    }

    /** List workspace directories that exist in data_dir */
    private List<String> listWorkspaces() {
        Path root = fileStore.resolve(FileStoreLayout.WORKSPACES);
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> s = Files.list(root)) {
            return s.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .toList();
        } catch (IOException e) { return List.of(); }
    }

    private Path feedsDir(String workspace) {
        return fileStore.resolve(FileStoreLayout.WORKSPACES, workspace, FEEDS_DIR);
    }

    private Path feedDir(String workspace, String name) {
        return feedsDir(workspace).resolve(name);
    }

    private String runtimeKey(String workspace, String name) {
        return workspace + "/" + name;
    }

    // ── Runtime state ───────────────────────────────────

    private static class FeedRuntime {
        final String workspace;
        final FeedConfig config;
        final FeedConnector connector;
        final AtomicLong messagesReceived = new AtomicLong();
        final AtomicLong featuresIngested = new AtomicLong();
        final AtomicLong errors = new AtomicLong();
        final Instant startedAt = Instant.now();
        volatile Instant lastMessageAt;
        volatile String lastError;
        volatile UUID sourceId;

        FeedRuntime(String workspace, FeedConfig config, FeedConnector connector) {
            this.workspace = workspace;
            this.config = config;
            this.connector = connector;
        }
    }
}