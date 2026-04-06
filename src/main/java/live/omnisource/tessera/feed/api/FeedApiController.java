package live.omnisource.tessera.feed.api;


import tools.jackson.databind.ObjectMapper;
import live.omnisource.tessera.feed.FeedService;
import live.omnisource.tessera.feed.dto.FeedConfig;
import live.omnisource.tessera.feed.dto.FeedStatus;
import live.omnisource.tessera.security.rbac.TesseraRole;
import live.omnisource.tessera.security.rbac.annotations.RequireWorkspaceRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/workspaces/{workspace}/feeds")
public class FeedApiController {

    private final FeedService feedService;
    private final ObjectMapper objectMapper;

    public FeedApiController(FeedService feedService, ObjectMapper objectMapper) {
        this.feedService = feedService;
        this.objectMapper = objectMapper;
    }

    // ── CRUD ────────────────────────────────────────────

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_VIEWER, workspaceParam = "workspace")
    @GetMapping
    public List<FeedConfig> list(@PathVariable String workspace) {
        return feedService.listFeeds(workspace);
    }

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_VIEWER, workspaceParam = "workspace")
    @GetMapping("/{name}")
    public ResponseEntity<FeedConfig> get(@PathVariable String workspace,
                                          @PathVariable String name) {
        return feedService.getFeed(workspace, name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_EDITOR, workspaceParam = "workspace")
    @PostMapping
    public ResponseEntity<?> create(@PathVariable String workspace,
                                    @RequestBody String body) {
        FeedConfig config;
        try {
            config = objectMapper.readValue(body, FeedConfig.class);
        } catch (Exception e) {
            log.warn("Failed to parse feed config: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid feed configuration",
                    "detail", e.getMessage()
            ));
        }

        try {
            var created = feedService.createFeed(workspace, config);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            log.warn("Failed to create feed '{}/{}': {}", workspace, config.name(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_EDITOR, workspaceParam = "workspace")
    @PutMapping("/{name}")
    public ResponseEntity<?> update(@PathVariable String workspace,
                                    @PathVariable String name,
                                    @RequestBody String body) {
        FeedConfig config;
        try {
            config = objectMapper.readValue(body, FeedConfig.class);
        } catch (Exception e) {
            log.warn("Failed to parse feed config for update: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid feed configuration",
                    "detail", e.getMessage()
            ));
        }

        try {
            var updated = feedService.updateFeed(workspace, name, config);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_EDITOR, workspaceParam = "workspace")
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String workspace,
                                       @PathVariable String name) {
        feedService.deleteFeed(workspace, name);
        return ResponseEntity.noContent().build();
    }

    // ── Lifecycle ───────────────────────────────────────

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_SYNC_OPERATOR, workspaceParam = "workspace")
    @PostMapping("/{name}/start")
    public ResponseEntity<?> start(@PathVariable String workspace,
                                   @PathVariable String name) {
        try {
            feedService.startFeed(workspace, name);
            return ResponseEntity.ok(feedService.getStatus(workspace, name));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_SYNC_OPERATOR, workspaceParam = "workspace")
    @PostMapping("/{name}/stop")
    public ResponseEntity<?> stop(@PathVariable String workspace,
                                  @PathVariable String name) {
        feedService.stopFeed(workspace, name);
        return ResponseEntity.ok(feedService.getStatus(workspace, name));
    }

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_VIEWER, workspaceParam = "workspace")
    @GetMapping("/{name}/status")
    public ResponseEntity<?> status(@PathVariable String workspace,
                                    @PathVariable String name) {
        var s = feedService.getStatus(workspace, name);
        if (s == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(s);
    }

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_VIEWER, workspaceParam = "workspace")
    @GetMapping("/status")
    public List<FeedStatus> allStatuses(@PathVariable String workspace) {
        return feedService.allStatuses(workspace);
    }

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_SYNC_OPERATOR, workspaceParam = "workspace")
    @PostMapping("/{name}/test")
    public ResponseEntity<?> test(@PathVariable String workspace,
                                  @PathVariable String name) {
        try {
            String result = feedService.testFeed(workspace, name);
            return ResponseEntity.ok(Map.of("success", true, "message", result));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }
}