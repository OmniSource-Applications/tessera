package live.omnisource.tessera.layer.api;

import live.omnisource.tessera.layer.LayerService;
import live.omnisource.tessera.layer.dto.LayerDto;
import live.omnisource.tessera.security.rbac.TesseraRole;
import live.omnisource.tessera.security.rbac.annotations.RequireGlobalRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Cross-workspace view of all layers across all workspaces.
 */
@RestController
@RequireGlobalRole(TesseraRole.TESSERA_OPS_VIEWER)
@RequestMapping("/api/layers")
public class LayersApiController {

    private final LayerService layerService;

    public LayersApiController(LayerService layerService) {
        this.layerService = layerService;
    }

    @GetMapping
    public List<LayerDto> listAll() {
        return layerService.listAllLayers();
    }

    @GetMapping("/count")
    public Map<String, Integer> count() {
        return Map.of("count", layerService.countAllLayers());
    }
}