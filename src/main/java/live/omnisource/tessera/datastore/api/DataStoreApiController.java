package live.omnisource.tessera.datastore.api;

import live.omnisource.tessera.datastore.DataStoreService;
import live.omnisource.tessera.datastore.dto.DataStoreDto;
import live.omnisource.tessera.model.dto.ExternalSourceCredentials;
import live.omnisource.tessera.security.rbac.TesseraRole;
import live.omnisource.tessera.security.rbac.annotations.RequireWorkspaceRole;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workspaces/{workspace}/datastores")
public class DataStoreApiController {

    private final DataStoreService dataStoreService;

    public DataStoreApiController(DataStoreService dataStoreService) {
        this.dataStoreService = dataStoreService;
    }

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_VIEWER, workspaceParam = "workspace")
    @GetMapping
    public List<String> list(@PathVariable String workspace) {
        return dataStoreService.listDataStores(workspace);
    }

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_VIEWER, workspaceParam = "workspace")
    @GetMapping("/{datastore}")
    public ResponseEntity<?> get(@PathVariable String workspace,
                                 @PathVariable String datastore) {
        try {
            var record = dataStoreService.getDataStore(new DataStoreDto(workspace, datastore));
            return ResponseEntity.ok(record);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_EDITOR, workspaceParam = "workspace")
    @PostMapping
    public ResponseEntity<?> create(@PathVariable String workspace,
                                    @RequestBody ExternalSourceCredentials credentials,
                                    @RequestParam String name) {
        try {
            var dto = new DataStoreDto(workspace, name);
            var info = dataStoreService.createDataStore(dto, credentials);

            if (info.connected()) {
                return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                        "workspace", workspace,
                        "datastore", name,
                        "connected", true,
                        "version", info.version()
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "connected", false,
                        "error", info.errorMessage()
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_EDITOR, workspaceParam = "workspace")
    @DeleteMapping("/{datastore}")
    public ResponseEntity<Void> delete(@PathVariable String workspace,
                                       @PathVariable String datastore) {
        try {
            dataStoreService.deleteDataStore(new DataStoreDto(workspace, datastore));
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}