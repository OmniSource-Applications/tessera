package live.omnisource.tessera.workspace.api;

import live.omnisource.tessera.security.rbac.TesseraRole;
import live.omnisource.tessera.security.rbac.annotations.RequireGlobalRole;
import live.omnisource.tessera.security.rbac.annotations.RequireWorkspaceRole;
import live.omnisource.tessera.workspace.WorkspaceService;
import live.omnisource.tessera.workspace.dto.WorkspaceDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceApiController {

    private final WorkspaceService workspaceService;

    public WorkspaceApiController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @RequireGlobalRole(TesseraRole.TESSERA_OPS_VIEWER)
    @GetMapping
    public List<String> list() {
        return workspaceService.listWorkspaces();
    }

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_VIEWER, workspaceParam = "name")
    @GetMapping("/{name}")
    public ResponseEntity<?> get(@PathVariable String name) {
        try {
            var ws = workspaceService.getWorkspace(new WorkspaceDto(name));
            return ResponseEntity.ok(Map.of(
                    "name", ws.name(),
                    "datastores", ws.dataSources().orElse(List.of())
            ));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @RequireGlobalRole(TesseraRole.TESSERA_PLATFORM_ADMIN)
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }
        try {
            workspaceService.createWorkspace(new WorkspaceDto(name));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("name", name));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_OWNER, workspaceParam = "name")
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        try {
            workspaceService.deleteWorkspace(new WorkspaceDto(name));
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}