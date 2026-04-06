package live.omnisource.tessera.datastore.api;

import live.omnisource.tessera.datastore.DataStoreService;
import live.omnisource.tessera.security.rbac.TesseraRole;
import live.omnisource.tessera.security.rbac.annotations.RequireGlobalRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequireGlobalRole(TesseraRole.TESSERA_OPS_VIEWER)
@RequestMapping("/api/sources")
public class SourcesApiController {

    private final DataStoreService dataStoreService;

    public SourcesApiController(DataStoreService dataStoreService) {
        this.dataStoreService = dataStoreService;
    }

    @GetMapping
    public List<Map<String, String>> listAll() {
        return dataStoreService.listAllDataStores().stream()
                .map(dto -> Map.of(
                        "workspace", dto.workspace(),
                        "datastore", dto.datastore()
                ))
                .toList();
    }

    @GetMapping("/count")
    public Map<String, Integer> count() {
        return Map.of("count", dataStoreService.countAllDataStores());
    }
}