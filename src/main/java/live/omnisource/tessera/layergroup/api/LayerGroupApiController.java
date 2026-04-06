package live.omnisource.tessera.layergroup.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import live.omnisource.tessera.layer.dto.LayerDto;
import live.omnisource.tessera.layergroup.LayerGroupService;
import live.omnisource.tessera.layergroup.dto.LayerGroupRecord;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API for layer group operations. Used by the admin UI (AJAX) and the map client for feature
 * queries.
 */
@Slf4j
@RestController
@RequestMapping("/api/layergroups")
public class LayerGroupApiController {

  private final LayerGroupService layerGroupService;

  public LayerGroupApiController(LayerGroupService layerGroupService) {
    this.layerGroupService = layerGroupService;
  }

  // ── CRUD ─────────────────────────────────────────────────

  /** List all groups with summary info. */
  @GetMapping
  public ResponseEntity<List<Map<String, Object>>> list() {
    List<Map<String, Object>> summaries =
        layerGroupService.listGroups().stream().map(this::toSummary).toList();
    return ResponseEntity.ok(summaries);
  }

  /** Create a new layer group. */
  @PostMapping
  public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, String> body) {
    String name = body.get("name");
    String description = body.getOrDefault("description", "");
    LayerGroupRecord record = layerGroupService.createGroup(name, description);
    return ResponseEntity.ok(toSummary(record));
  }

  /** Get a group with full member details. */
  @GetMapping("/{name}")
  public ResponseEntity<LayerGroupRecord> get(@PathVariable String name) {
    return ResponseEntity.ok(layerGroupService.getGroup(name));
  }

  /** Delete a group. */
  @DeleteMapping("/{name}")
  public ResponseEntity<Void> delete(@PathVariable String name) {
    layerGroupService.deleteGroup(name);
    return ResponseEntity.noContent().build();
  }

  /** Toggle active flag. */
  @PutMapping("/{name}/active")
  public ResponseEntity<LayerGroupRecord> toggleActive(
      @PathVariable String name, @RequestBody Map<String, Boolean> body) {
    boolean active = body.getOrDefault("active", true);
    return ResponseEntity.ok(layerGroupService.toggleActive(name, active));
  }

  // ── Member management ────────────────────────────────────

  /**
   * Add a member layer to a group. Body: { "workspace": "...", "datastore": "...", "layer": "..." }
   */
  @PostMapping("/{name}/members")
  public ResponseEntity<LayerGroupRecord> addMember(
      @PathVariable String name, @RequestBody Map<String, String> body) {
    LayerDto dto = new LayerDto(body.get("workspace"), body.get("datastore"), body.get("layer"));
    return ResponseEntity.ok(layerGroupService.addMember(name, dto));
  }

  /**
   * Remove a member layer from a group. Body: { "workspace": "...", "datastore": "...", "layer":
   * "..." }
   */
  @DeleteMapping("/{name}/members")
  public ResponseEntity<LayerGroupRecord> removeMember(
      @PathVariable String name, @RequestBody Map<String, String> body) {
    return ResponseEntity.ok(
        layerGroupService.removeMember(
            name, body.get("workspace"), body.get("datastore"), body.get("layer")));
  }

  // ── Feature queries (used by map) ────────────────────────

  /**
   * Query geo_features within a bounding box for all sources in a group. Body: { "minLon": ...,
   * "minLat": ..., "maxLon": ..., "maxLat": ..., "limit": 1000, "offset": 0 }
   */
  @PostMapping("/{name}/features")
  public ResponseEntity<Map<String, Object>> queryFeatures(
      @PathVariable String name, @RequestBody Map<String, Object> body) {

    double minLon = toDouble(body.get("minLon"));
    double minLat = toDouble(body.get("minLat"));
    double maxLon = toDouble(body.get("maxLon"));
    double maxLat = toDouble(body.get("maxLat"));
    int limit = toInt(body.getOrDefault("limit", 1000));
    int offset = toInt(body.getOrDefault("offset", 0));

    try {
      var rows =
          layerGroupService.queryFeaturesByBbox(
              name, minLon, minLat, maxLon, maxLat, limit, offset);

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("success", true);
      result.put("group", name);
      result.put("rowCount", rows.size());
      result.put("rows", rows);
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      log.warn("Feature query failed for group '{}': {}", name, e.getMessage());
      Map<String, Object> err = new LinkedHashMap<>();
      err.put("success", false);
      err.put("error", e.getMessage());
      return ResponseEntity.ok(err);
    }
  }

  /** Feature count per source in a group. */
  @GetMapping("/{name}/stats")
  public ResponseEntity<List<Map<String, Object>>> stats(@PathVariable String name) {
    return ResponseEntity.ok(layerGroupService.queryFeatureCount(name));
  }

  // ── Helpers ──────────────────────────────────────────────

  private Map<String, Object> toSummary(LayerGroupRecord record) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("name", record.name());
    map.put("description", record.description());
    map.put("active", record.active());
    map.put("memberCount", record.members().size());
    map.put("createdAt", record.createdAt());
    map.put("updatedAt", record.updatedAt());
    return map;
  }

  private static double toDouble(Object val) {
    if (val instanceof Number n) return n.doubleValue();
    return Double.parseDouble(val.toString());
  }

  private static int toInt(Object val) {
    if (val instanceof Number n) return n.intValue();
    return Integer.parseInt(val.toString());
  }
}
