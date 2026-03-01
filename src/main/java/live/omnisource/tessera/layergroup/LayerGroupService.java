package live.omnisource.tessera.layergroup;

import com.fasterxml.jackson.databind.ObjectMapper;
import live.omnisource.tessera.exceptions.LayerGroupAlreadyExistsException;
import live.omnisource.tessera.exceptions.LayerGroupNotFoundException;
import live.omnisource.tessera.exceptions.LayerGroupValidationException;
import live.omnisource.tessera.filestore.FileStoreLayout;
import live.omnisource.tessera.filestore.FileStoreService;
import live.omnisource.tessera.layer.LayerService;
import live.omnisource.tessera.layer.dto.LayerDto;
import live.omnisource.tessera.layer.dto.LayerRecord;
import live.omnisource.tessera.layergroup.dto.LayerGroupMember;
import live.omnisource.tessera.layergroup.dto.LayerGroupRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * File-based CRUD for layer groups + direct JDBC queries for geo_features.
 *
 * <p>Storage layout:</p>
 * <pre>
 *   data_dir/etc/catalog/layergroups/{group-name}/group.json
 * </pre>
 *
 * <p>Feature queries use the cached source_id UUIDs from group.json to
 * filter geo_features with {@code source_id = ANY(?::uuid[])}. No
 * additional database tables are needed.</p>
 */
@Slf4j
@Service
public class LayerGroupService {

    private static final String METADATA_FILE = "group.json";
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$");

    private final FileStoreService fileStoreService;
    private final LayerService layerService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public LayerGroupService(FileStoreService fileStoreService,
                             LayerService layerService,
                             JdbcTemplate jdbcTemplate,
                             ObjectMapper objectMapper) {
        this.fileStoreService = fileStoreService;
        this.layerService = layerService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    // ── CRUD ─────────────────────────────────────────────────

    /**
     * Create a new empty layer group.
     */
    public LayerGroupRecord createGroup(String name, String description) {
        String normalized = normalizeName(name);
        Path dir = groupDir(normalized);

        if (Files.exists(dir)) {
            throw new LayerGroupAlreadyExistsException(normalized);
        }

        LayerGroupRecord record = LayerGroupRecord.create(normalized, description);

        try {
            Files.createDirectories(dir);
            byte[] json = objectMapper.writeValueAsBytes(record);
            fileStoreService.writeAtomic(dir.resolve(METADATA_FILE), json);
            log.info("Created layer group: {}", normalized);
        } catch (IOException e) {
            throw new LayerGroupValidationException("Failed to create layer group: " + e.getMessage());
        }

        return record;
    }

    /**
     * List all layer groups, returning their full records.
     */
    public List<LayerGroupRecord> listGroups() {
        Path root = groupsRoot();
        if (!Files.isDirectory(root)) return List.of();

        try (Stream<Path> dirs = Files.list(root)) {
            return dirs.filter(Files::isDirectory)
                    .map(dir -> {
                        Path file = dir.resolve(METADATA_FILE);
                        if (!Files.exists(file)) return null;
                        try {
                            return objectMapper.readValue(Files.readAllBytes(file), LayerGroupRecord.class);
                        } catch (IOException e) {
                            log.warn("Failed to read group {}: {}", dir.getFileName(), e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(LayerGroupRecord::name))
                    .toList();
        } catch (IOException e) {
            throw new LayerGroupValidationException("Failed to list layer groups: " + e.getMessage());
        }
    }

    /**
     * Get a single group by name.
     */
    public LayerGroupRecord getGroup(String name) {
        Path file = groupDir(name).resolve(METADATA_FILE);
        if (!Files.exists(file)) {
            throw new LayerGroupNotFoundException(name);
        }
        try {
            return objectMapper.readValue(Files.readAllBytes(file), LayerGroupRecord.class);
        } catch (IOException e) {
            throw new LayerGroupValidationException("Failed to read layer group: " + e.getMessage());
        }
    }

    /**
     * Delete a group and its directory.
     */
    public void deleteGroup(String name) {
        Path dir = groupDir(name);
        if (!Files.exists(dir)) return;

        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
            log.info("Deleted layer group: {}", name);
        } catch (IOException e) {
            throw new LayerGroupValidationException("Failed to delete layer group: " + e.getMessage());
        }
    }

    /**
     * Toggle active flag.
     */
    public LayerGroupRecord toggleActive(String name, boolean active) {
        LayerGroupRecord record = getGroup(name).withActive(active);
        writeRecord(name, record);
        log.info("Layer group '{}' active={}", name, active);
        return record;
    }

    public int countGroups() {
        Path root = groupsRoot();
        if (!Files.isDirectory(root)) return 0;
        try (Stream<Path> dirs = Files.list(root)) {
            return (int) dirs.filter(Files::isDirectory)
                    .filter(d -> Files.exists(d.resolve(METADATA_FILE)))
                    .count();
        } catch (IOException e) {
            return 0;
        }
    }

    // ── Member management ────────────────────────────────────

    /**
     * Add a layer to a group. Resolves source_id from external_sources and
     * source_table from the layer record. The layer must have been synced
     * at least once (so that an external_sources row exists).
     */
    public LayerGroupRecord addMember(String groupName, LayerDto layerDto) {
        LayerGroupRecord group = getGroup(groupName);

        // 1. Read layer metadata for source_table
        LayerRecord layerRecord = layerService.getLayer(layerDto);
        String sourceTable = layerRecord.sourceSchema() + "." + layerRecord.sourceTable();

        // 2. Resolve source_id from external_sources
        String sourceName = layerDto.workspace() + "/" + layerDto.datastore() + "/" + layerDto.layer();
        String sourceId = resolveSourceId(sourceName);
        if (sourceId == null) {
            throw new LayerGroupValidationException(
                    "Layer '" + sourceName + "' has not been synced yet. "
                    + "Sync the layer first so its features are indexed.");
        }

        // 3. Build member and add (deduplicates by layer path)
        LayerGroupMember member = new LayerGroupMember(
                layerDto.workspace(), layerDto.datastore(), layerDto.layer(),
                sourceId, sourceTable
        );

        LayerGroupRecord updated = group.withMemberAdded(member);
        writeRecord(groupName, updated);

        log.info("Added member '{}' to group '{}' (sourceId={}, sourceTable={})",
                sourceName, groupName, sourceId, sourceTable);
        return updated;
    }

    /**
     * Remove a member from a group by layer path.
     */
    public LayerGroupRecord removeMember(String groupName, String workspace, String datastore, String layer) {
        LayerGroupRecord group = getGroup(groupName);
        LayerGroupRecord updated = group.withMemberRemoved(workspace, datastore, layer);
        writeRecord(groupName, updated);

        log.info("Removed member '{}/{}/{}' from group '{}'",
                workspace, datastore, layer, groupName);
        return updated;
    }

    // ── Feature queries (direct JDBC) ────────────────────────

    /**
     * Query geo_features within a bounding box for all sources in a group.
     * Returns raw row maps suitable for JSON serialization.
     */
    public List<Map<String, Object>> queryFeaturesByBbox(
            String groupName,
            double minLon, double minLat, double maxLon, double maxLat,
            int limit, int offset) {

        LayerGroupRecord group = getGroup(groupName);
        String[] sourceIds = group.sourceIds();

        if (sourceIds.length == 0) return List.of();

        return jdbcTemplate.queryForList("""
                SELECT f.id, f.external_id, f.source_id, f.source_table,
                       ST_AsGeoJSON(f.geometry)::jsonb AS geometry,
                       f.attributes, f.updated_at
                FROM tessera.geo_features f
                WHERE ST_Intersects(f.geometry, ST_MakeEnvelope(?, ?, ?, ?, 4326))
                  AND f.source_id = ANY(?::uuid[])
                ORDER BY f.updated_at DESC
                LIMIT ? OFFSET ?
                """,
                minLon, minLat, maxLon, maxLat,
                sourceIds,
                limit, offset);
    }

    /**
     * Feature count per source in a group.
     */
    public List<Map<String, Object>> queryFeatureCount(String groupName) {
        LayerGroupRecord group = getGroup(groupName);
        String[] sourceIds = group.sourceIds();

        if (sourceIds.length == 0) return List.of();

        return jdbcTemplate.queryForList("""
                SELECT f.source_id, f.source_table, count(*) AS feature_count
                FROM tessera.geo_features f
                WHERE f.source_id = ANY(?::uuid[])
                GROUP BY f.source_id, f.source_table
                ORDER BY feature_count DESC
                """,
                (Object) sourceIds);
    }

    // ── Source resolution ─────────────────────────────────────

    /**
     * Look up the external_sources.id for a layer by its name convention
     * (workspace/datastore/layer). Returns null if the layer hasn't been
     * synced yet.
     */
    private String resolveSourceId(String sourceName) {
        var rows = jdbcTemplate.queryForList(
                "SELECT id FROM tessera.external_sources WHERE name = ?",
                sourceName);
        if (rows.isEmpty()) return null;
        return rows.getFirst().get("id").toString();
    }

    // ── Persistence helpers ──────────────────────────────────

    private void writeRecord(String name, LayerGroupRecord record) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(record);
            fileStoreService.writeAtomic(groupDir(name).resolve(METADATA_FILE), json);
        } catch (IOException e) {
            throw new LayerGroupValidationException("Failed to write layer group: " + e.getMessage());
        }
    }

    // ── Path helpers ─────────────────────────────────────────

    private Path groupsRoot() {
        return fileStoreService.resolve(FileStoreLayout.LAYER_GROUPS);
    }

    private Path groupDir(String name) {
        return groupsRoot().resolve(normalizeName(name)).normalize();
    }

    // ── Validation ───────────────────────────────────────────

    private static String normalizeName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new LayerGroupValidationException("Layer group name is required.");
        }
        String name = raw.trim();
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new LayerGroupValidationException(
                    "Invalid layer group name '" + name + "'. Use [a-zA-Z0-9][a-zA-Z0-9_-]{0,63}.");
        }
        return name;
    }
}
