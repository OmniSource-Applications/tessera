package live.omnisource.tessera.web;

import live.omnisource.tessera.layer.LayerService;
import live.omnisource.tessera.layer.dto.IntrospectionResult;
import live.omnisource.tessera.layer.dto.LayerDto;
import live.omnisource.tessera.layer.dto.LayerRecord;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/workspaces/{workspace}/datastores/{datastore}/layers")
public class LayerController {

    private final LayerService layerService;

    public LayerController(LayerService layerService) {
        this.layerService = layerService;
    }

    /**
     * Introspect the data source and show discovered spatial tables
     * alongside existing layers.
     */
    @GetMapping
    public String list(@PathVariable String workspace,
                       @PathVariable String datastore,
                       Model model) {

        var layers = layerService.listLayers(workspace, datastore);

        IntrospectionResult introspection = null;
        String introspectionError = null;
        try {
            introspection = layerService.introspect(workspace, datastore);
        } catch (Exception e) {
            introspectionError = e.getMessage();
        }

        model.addAttribute("title", "Layers — " + datastore);
        model.addAttribute("description", "Spatial layers in " + datastore);
        model.addAttribute("view", "layers/list");
        model.addAttribute("workspace", workspace);
        model.addAttribute("datastore", datastore);
        model.addAttribute("layers", layers);
        model.addAttribute("introspection", introspection);
        model.addAttribute("introspectionError", introspectionError);
        return "layout/page";
    }

    /**
     * Create a layer from an introspected table.
     */
    @PostMapping
    public String create(@PathVariable String workspace,
                         @PathVariable String datastore,
                         @RequestParam String name,
                         @RequestParam String sourceSchema,
                         @RequestParam String sourceTable,
                         @RequestParam String geometryColumn,
                         @RequestParam String geometryType,
                         @RequestParam int srid,
                         @RequestParam long rowCount,
                         @RequestParam(required = false) String extent,
                         RedirectAttributes redirect) {
        try {
            double[] parsedExtent = parseExtent(extent);

            var table = new IntrospectionResult.SpatialTable(
                    sourceSchema, sourceTable, geometryColumn,
                    geometryType, srid, rowCount, parsedExtent
            );
            var dto = new LayerDto(workspace, datastore, name);
            layerService.createLayer(dto, table);

            redirect.addFlashAttribute("success",
                    "Layer '" + name + "' created from " + sourceSchema + "." + sourceTable);
        } catch (Exception e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/workspaces/" + workspace + "/datastores/" + datastore + "/layers";
    }

    /**
     * Layer detail page — includes sync metadata and config.
     */
    @GetMapping("/{layer}")
    public String detail(@PathVariable String workspace,
                         @PathVariable String datastore,
                         @PathVariable String layer,
                         Model model) {
        var record = layerService.getLayer(new LayerDto(workspace, datastore, layer));
        var syncMeta = layerService.getSyncMeta(workspace, datastore, layer);

        model.addAttribute("title", layer);
        model.addAttribute("description", "Layer details");
        model.addAttribute("view", "layers/detail");
        model.addAttribute("workspace", workspace);
        model.addAttribute("datastore", datastore);
        model.addAttribute("layer", record);
        model.addAttribute("syncMeta", syncMeta);
        return "layout/page";
    }

    /**
     * Save auto-sync configuration for a layer.
     */
    @PostMapping("/{layer}/sync/config")
    public String saveSyncConfig(@PathVariable String workspace,
                                 @PathVariable String datastore,
                                 @PathVariable String layer,
                                 @RequestParam(defaultValue = "false") boolean enabled,
                                 @RequestParam(defaultValue = "300") int pollIntervalSeconds,
                                 @RequestParam(required = false) String orderByColumn,
                                 RedirectAttributes redirect) {
        try {
            var config = new LayerRecord.SyncConfig(
                    enabled,
                    Math.max(pollIntervalSeconds, 30),
                    orderByColumn != null && !orderByColumn.isBlank()
                            ? orderByColumn.trim() : null
            );
            layerService.updateSyncConfig(
                    new LayerDto(workspace, datastore, layer), config);

            String msg = enabled
                    ? "Auto-sync enabled — polling every " + Math.max(pollIntervalSeconds, 30) + "s"
                    : "Auto-sync disabled";
            redirect.addFlashAttribute("success", msg);
        } catch (Exception e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/workspaces/" + workspace + "/datastores/"
                + datastore + "/layers/" + layer;
    }

    /**
     * Delete a layer.
     */
    @PostMapping("/{layer}/delete")
    public String delete(@PathVariable String workspace,
                         @PathVariable String datastore,
                         @PathVariable String layer,
                         RedirectAttributes redirect) {
        try {
            layerService.deleteLayer(new LayerDto(workspace, datastore, layer));
            redirect.addFlashAttribute("success", "Layer '" + layer + "' deleted.");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/workspaces/" + workspace + "/datastores/" + datastore + "/layers";
    }

    private double[] parseExtent(String extent) {
        if (extent == null || extent.isBlank()) return null;
        try {
            String[] parts = extent.split(",");
            return new double[]{
                    Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()),
                    Double.parseDouble(parts[3].trim())
            };
        } catch (Exception e) {
            return null;
        }
    }
}