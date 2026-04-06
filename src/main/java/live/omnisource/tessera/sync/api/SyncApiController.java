package live.omnisource.tessera.sync.api;

import live.omnisource.tessera.security.rbac.TesseraRole;
import live.omnisource.tessera.security.rbac.annotations.RequireWorkspaceRole;
import live.omnisource.tessera.sync.AsyncSyncRunner;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/workspaces/{workspace}/datastores/{datastore}/layers/{layer}/sync")
public class SyncApiController {

    private final AsyncSyncRunner asyncRunner;

    public SyncApiController(AsyncSyncRunner asyncRunner) {
        this.asyncRunner = asyncRunner;
    }

    /**
     * Trigger an async sync for a layer.
     * Returns immediately; sync runs in the background.
     */
    @RequireWorkspaceRole(value = TesseraRole.TESSERA_SYNC_OPERATOR, workspaceParam = "workspace")
    @PostMapping
    public ResponseEntity<Map<String, Object>> trigger(@PathVariable String workspace,
                                                       @PathVariable String datastore,
                                                       @PathVariable String layer) {
        asyncRunner.run(workspace, datastore, layer);
        return ResponseEntity.accepted().body(Map.of(
                "status", "STARTED",
                "workspace", workspace,
                "datastore", datastore,
                "layer", layer,
                "message", "Sync started in background"
        ));
    }
}