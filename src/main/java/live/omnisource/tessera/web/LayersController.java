package live.omnisource.tessera.web;

import live.omnisource.tessera.layer.LayerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/layers")
public class LayersController {
    private final LayerService layersService;

    public LayersController(LayerService layersService) {
        this.layersService = layersService;
    }

    @GetMapping
    public String list(Model model) {
        var layers = layersService.listAllLayers();
        model.addAttribute("title", "Layers");
        model.addAttribute("description", "All layers across workspaces.");
        model.addAttribute("view", "layerslist/list");
        model.addAttribute("layers", layers);
        return "layout/page";
    }
}
