package live.omnisource.tessera.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import live.omnisource.tessera.datasource.connector.DataSourceConnector;
import live.omnisource.tessera.datasource.connector.DataSourceConnector.StreamOptions;
import live.omnisource.tessera.exceptions.DataStoreNotFoundException;
import live.omnisource.tessera.exceptions.DataStoreValidationException;
import live.omnisource.tessera.filestore.FileStoreLayout;
import live.omnisource.tessera.filestore.FileStoreService;
import live.omnisource.tessera.layer.dto.LayerRecord;
import live.omnisource.tessera.model.dto.ColumnMetadata;
import live.omnisource.tessera.model.dto.RawRecord;
import live.omnisource.tessera.model.dto.SchemaMetadata;
import live.omnisource.tessera.model.entity.ExternalSource;
import live.omnisource.tessera.repository.ExternalSourceRepository;
import live.omnisource.tessera.sync.dto.ExtractedFeature;
import live.omnisource.tessera.sync.dto.SyncJobResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * Orchestrates the sync pipeline for a layer:
 *
 *   1. Read layer + datastore metadata
 *   2. Register (or find) an external_source record
 *   3. Determine sync mode: incremental (orderByColumn + checkpoint) or full rescan
 *   4. Stream rows from the external source
 *   5. Extract geometry + attributes → ExtractedFeature
 *   6. Batch write to geo_features + h3_cell_index
 *   7. Update sync checkpoint
 *
 * Incremental sync (orderByColumn configured):
 *   - Reads checkpoint from sync_checkpoints table
 *   - Uses StreamOptions.since(column, value) to read only new/updated rows
 *   - Tracks the max checkpoint value during streaming
 *
 * Full rescan (no orderByColumn):
 *   - Reads all rows, skips those whose external_id + data_hash already exist
 *   - More expensive but works for sources without a reliable ordering column
 */
@Slf4j
@Service
public class FeatureSyncService {

    /** Default H3 resolutions to index at */
    private static final int[] DEFAULT_H3_RESOLUTIONS = {7, 9};

    private static final int BATCH_SIZE = 500;

    private final FileStoreService fileStoreService;
    private final Map<String, DataSourceConnector> connectionFactories;
    private final ExternalSourceRepository externalSourceRepo;
    private final FeatureBatchWriter batchWriter;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public FeatureSyncService(FileStoreService fileStoreService,
                              Map<String, DataSourceConnector> connectionFactories,
                              ExternalSourceRepository externalSourceRepo,
                              FeatureBatchWriter batchWriter,
                              JdbcTemplate jdbcTemplate,
                              ObjectMapper objectMapper) {
        this.fileStoreService = fileStoreService;
        this.connectionFactories = connectionFactories;
        this.externalSourceRepo = externalSourceRepo;
        this.batchWriter = batchWriter;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Sync a layer — automatically chooses incremental or full rescan
     * based on whether the layer has an orderByColumn configured and
     * whether a previous checkpoint exists.
     */
    public SyncJobResult syncLayer(String workspace, String datastore, String layerName) {
        Instant start = Instant.now();
        long read = 0, written = 0, skipped = 0;

        try {
            // 1. Load metadata
            LayerRecord layer = loadLayerRecord(workspace, datastore, layerName);
            String type = loadDataStoreType(workspace, datastore);
            DataSourceConnector connector = resolveConnector(type);
            String secretKey = "workspaces/" + workspace + "/datastores/" + datastore;
            String qualifiedTable = layer.sourceSchema() + "." + layer.sourceTable();

            // 2. Register or find external source
            UUID sourceId = ensureExternalSource(
                    workspace, datastore, layerName, type, secretKey, layer);

            // 3. Discover PK columns for stable external IDs
            String[] pkColumns = discoverPkColumns(connector, secretKey, layer);

            // 4. Determine sync mode
            String orderByColumn = layer.syncConfig() != null
                    ? layer.syncConfig().orderByColumn() : null;
            String checkpoint = readCheckpointValue(sourceId, qualifiedTable);

            boolean incremental = orderByColumn != null && checkpoint != null;

            StreamOptions opts = incremental
                    ? StreamOptions.since(orderByColumn, checkpoint)
                    : StreamOptions.full();

            log.info("Starting {} sync: {}/{}/{} → {} (checkpoint: {})",
                    incremental ? "incremental" : "full",
                    workspace, datastore, layerName, qualifiedTable,
                    checkpoint != null ? checkpoint : "none");

            // 5. For full rescan dedup: load existing hashes
            Set<String> existingHashes = Set.of();
            if (!incremental) {
                existingHashes = loadExistingHashes(sourceId, qualifiedTable);
            }

            // 6. Build extractor and stream
            FeatureExtractor extractor = new FeatureExtractor(
                    layer.geometryColumn(), pkColumns);
            List<ExtractedFeature> batch = new ArrayList<>(BATCH_SIZE);
            Object maxCheckpointValue = null;

            try (Stream<RawRecord> stream = connector.streamTable(
                    secretKey, layer.sourceSchema(), layer.sourceTable(), opts)) {

                Iterator<RawRecord> it = stream.iterator();
                while (it.hasNext()) {
                    RawRecord raw = it.next();
                    read++;

                    ExtractedFeature feature = extractor.extract(raw);
                    if (feature == null) {
                        skipped++;
                        continue;
                    }

                    // Full-rescan dedup: skip if hash already exists
                    if (!incremental && !existingHashes.isEmpty()) {
                        String hashHex = bytesToHex(feature.dataHash());
                        if (existingHashes.contains(hashHex)) {
                            skipped++;
                            continue;
                        }
                    }

                    batch.add(feature);

                    // Track max value of the ordering column for next checkpoint
                    if (orderByColumn != null) {
                        Object val = raw.get(orderByColumn);
                        if (val != null) {
                            maxCheckpointValue = val;
                        }
                    }

                    if (batch.size() >= BATCH_SIZE) {
                        written += batchWriter.writeBatch(
                                sourceId, qualifiedTable, batch, DEFAULT_H3_RESOLUTIONS);
                        batch.clear();

                        if (read % 5000 == 0) {
                            log.info("Sync progress: {}/{}/{} — read={} written={} skipped={}",
                                    workspace, datastore, layerName, read, written, skipped);
                        }
                    }
                }

                // Flush remaining
                if (!batch.isEmpty()) {
                    written += batchWriter.writeBatch(
                            sourceId, qualifiedTable, batch, DEFAULT_H3_RESOLUTIONS);
                    batch.clear();
                }
            }

            // 7. Update checkpoint
            String newCheckpoint = maxCheckpointValue != null
                    ? maxCheckpointValue.toString()
                    : Instant.now().toString();
            updateCheckpoint(sourceId, qualifiedTable, written, newCheckpoint);

            // 8. Write sync metadata for UI
            updateLayerSyncMetadata(workspace, datastore, layerName, written, incremental);

            long h3Cells = written * DEFAULT_H3_RESOLUTIONS.length;
            log.info("Sync completed: {}/{}/{} — mode={} read={} written={} skipped={} " +
                            "h3Cells={} duration={}s",
                    workspace, datastore, layerName,
                    incremental ? "incremental" : "full",
                    read, written, skipped, h3Cells,
                    java.time.Duration.between(start, Instant.now()).toSeconds());

            return SyncJobResult.completed(workspace, datastore, layerName,
                    read, written, skipped, h3Cells, start);

        } catch (Exception e) {
            log.error("Sync failed for {}/{}/{}: {}",
                    workspace, datastore, layerName, e.getMessage(), e);
            return SyncJobResult.failed(workspace, datastore, layerName,
                    read, written, skipped, start, e.getMessage());
        }
    }

    // ── Checkpoint ────────────────────────────────────────────

    /**
     * Read the last checkpoint value for a source+table pair.
     * Returns null if no checkpoint exists (triggers full sync).
     */
    private String readCheckpointValue(UUID sourceId, String tableName) {
        var rows = jdbcTemplate.queryForList("""
                SELECT checkpoint_value FROM tessera.sync_checkpoints
                WHERE source_id = ?::uuid AND table_name = ?
                """, sourceId.toString(), tableName);
        return rows.isEmpty() ? null : (String) rows.getFirst().get("checkpoint_value");
    }

    private void updateCheckpoint(UUID sourceId, String tableName,
                                  long rowsWritten, String checkpointValue) {
        jdbcTemplate.update("""
                INSERT INTO tessera.sync_checkpoints
                    (source_id, table_name, checkpoint_type, checkpoint_value,
                     rows_processed, last_synced_at)
                VALUES (?::uuid, ?, 'TIMESTAMP', ?, ?, now())
                ON CONFLICT (source_id, table_name)
                DO UPDATE SET
                    checkpoint_value = EXCLUDED.checkpoint_value,
                    rows_processed = tessera.sync_checkpoints.rows_processed
                                    + EXCLUDED.rows_processed,
                    last_synced_at = now()
                """,
                sourceId.toString(), tableName,
                checkpointValue, rowsWritten);
    }

    // ── Full-rescan dedup ─────────────────────────────────────

    /**
     * Load all existing data_hash values for a source+table.
     * Used during full rescan to skip rows that haven't changed.
     */
    private Set<String> loadExistingHashes(UUID sourceId, String qualifiedTable) {
        try {
            var rows = jdbcTemplate.queryForList("""
                    SELECT encode(data_hash, 'hex') AS hash
                    FROM tessera.geo_features
                    WHERE source_id = ?::uuid AND source_table = ?
                      AND data_hash IS NOT NULL
                    """, sourceId.toString(), qualifiedTable);
            Set<String> hashes = new HashSet<>(rows.size());
            for (var row : rows) {
                hashes.add((String) row.get("hash"));
            }
            log.debug("Loaded {} existing hashes for dedup on {}", hashes.size(), qualifiedTable);
            return hashes;
        } catch (Exception e) {
            log.warn("Could not load existing hashes for dedup: {}", e.getMessage());
            return Set.of();
        }
    }

    // ── External source registration ──────────────────────────

    private UUID ensureExternalSource(String workspace, String datastore,
                                      String layer, String type, String secretKey,
                                      LayerRecord record) {
        String sourceName = workspace + "/" + datastore + "/" + layer;

        var existing = jdbcTemplate.queryForList(
                "SELECT id FROM tessera.external_sources WHERE name = ?", sourceName);
        if (!existing.isEmpty()) {
            return (UUID) existing.getFirst().get("id");
        }

        ExternalSource source = new ExternalSource();
        source.setName(sourceName);
        source.setSourceType(ExternalSource.SourceType.valueOf(type.toUpperCase()));
        source.setConnectionKey(secretKey);
        source.setActive(true);
        source.setGeometryColumn(record.geometryColumn());
        source.setSyncStrategy(ExternalSource.SyncStrategy.BATCH);
        source.setLastIntrospect(Instant.now());

        source = externalSourceRepo.save(source);
        log.info("Registered external source: {} → {}", sourceName, source.getId());
        return source.getId();
    }

    // ── PK discovery ──────────────────────────────────────────

    private String[] discoverPkColumns(DataSourceConnector connector, String secretKey,
                                       LayerRecord layer) {
        try {
            SchemaMetadata meta = connector.introspectTable(
                    secretKey, layer.sourceSchema(), layer.sourceTable());
            return meta.columns().stream()
                    .filter(ColumnMetadata::isPrimaryKey)
                    .map(ColumnMetadata::name)
                    .toArray(String[]::new);
        } catch (Exception e) {
            log.warn("Could not discover PKs for {}.{}, using hash-based IDs: {}",
                    layer.sourceSchema(), layer.sourceTable(), e.getMessage());
            return new String[0];
        }
    }

    // ── Metadata I/O ──────────────────────────────────────────

    private LayerRecord loadLayerRecord(String ws, String ds, String layer) {
        Path file = layerDir(ws, ds, layer).resolve("layer.json");
        if (!Files.exists(file)) {
            throw new DataStoreNotFoundException("Layer not found: " + layer);
        }
        try {
            return objectMapper.readValue(Files.readAllBytes(file), LayerRecord.class);
        } catch (IOException e) {
            throw new DataStoreValidationException(
                    "Failed to read layer metadata: " + e.getMessage());
        }
    }

    private String loadDataStoreType(String ws, String ds) {
        Path file = dataStoreDir(ws, ds).resolve("datastore.json");
        try {
            var tree = objectMapper.readTree(Files.readAllBytes(file));
            return tree.get("type").asText();
        } catch (IOException e) {
            throw new DataStoreValidationException(
                    "Failed to read datastore metadata: " + e.getMessage());
        }
    }

    private DataSourceConnector resolveConnector(String type) {
        DataSourceConnector connector = connectionFactories.get(type.toLowerCase());
        if (connector == null) {
            throw new DataStoreValidationException("Unsupported connector: " + type);
        }
        return connector;
    }

    private void updateLayerSyncMetadata(String ws, String ds, String layer,
                                         long featuresWritten, boolean wasIncremental) {
        Path syncFile = layerDir(ws, ds, layer).resolve("sync.json");
        try {
            // Read existing to accumulate totals
            long totalIngested = featuresWritten;
            int syncCount = 1;
            if (Files.exists(syncFile)) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> existing = objectMapper.readValue(
                            Files.readAllBytes(syncFile), Map.class);
                    if (existing.get("totalIngested") instanceof Number n) {
                        totalIngested += n.longValue();
                    }
                    if (existing.get("syncCount") instanceof Number n) {
                        syncCount += n.intValue();
                    }
                } catch (Exception ignored) {}
            }

            var meta = Map.of(
                    "lastSync", Instant.now().toString(),
                    "lastMode", wasIncremental ? "incremental" : "full",
                    "lastBatchSize", featuresWritten,
                    "totalIngested", totalIngested,
                    "syncCount", syncCount,
                    "h3Resolutions", DEFAULT_H3_RESOLUTIONS,
                    "status", "SYNCED"
            );
            fileStoreService.writeAtomic(syncFile, objectMapper.writeValueAsBytes(meta));
        } catch (IOException e) {
            log.warn("Failed to write sync metadata for layer {}: {}", layer, e.getMessage());
        }
    }

    // ── Path helpers ──────────────────────────────────────────

    private Path dataStoreDir(String ws, String ds) {
        return fileStoreService.resolve(FileStoreLayout.WORKSPACES)
                .resolve(ws).resolve("data").resolve(ds);
    }

    private Path layerDir(String ws, String ds, String layer) {
        return dataStoreDir(ws, ds).resolve("layers").resolve(layer);
    }

    // ── Util ──────────────────────────────────────────────────

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        var sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}