package live.omnisource.tessera.web;

import live.omnisource.tessera.datastore.DataStoreService;
import live.omnisource.tessera.workspace.WorkspaceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private static final String LAYOUT = "layout/page";
    private static final String TITLE = "title";
    private static final String DESCRIPTION = "description";
    private static final String VIEW = "view";

    private final WorkspaceService workspaceService;
    private final DataStoreService dataStoreService;

    public HomeController(WorkspaceService workspaceService,
                          DataStoreService dataStoreService) {
        this.workspaceService = workspaceService;
        this.dataStoreService = dataStoreService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("title", "Dashboard");
        model.addAttribute("description", "Overview of indexed data sources and live streams.");
        model.addAttribute("view", "home");
        model.addAttribute("workspacesCount", workspaceService.countWorkspaces());
        model.addAttribute("sourcesCount", dataStoreService.countAllDataStores());
        return LAYOUT;
    }

    @GetMapping("/layers")
    public String layers(Model model) {
        model.addAttribute(TITLE, "Layers");
        model.addAttribute(DESCRIPTION, "Manage your data layers.");
        model.addAttribute(VIEW, "layer");
        return LAYOUT;
    }

    @GetMapping("/layergroups")
    public String layergroups(Model model) {
        model.addAttribute(TITLE, "Layer Groups");
        model.addAttribute(DESCRIPTION, "Build layer groups for data visualization.");
        model.addAttribute(VIEW, "layer");
        return LAYOUT;
    }

    @GetMapping("/query")
    public String query(Model model) {
        model.addAttribute(TITLE, "Query");
        model.addAttribute(VIEW, "query");
        return LAYOUT;
    }

    @GetMapping("/streams")
    public String streams(Model model) {
        model.addAttribute(TITLE, "Streams");
        model.addAttribute(DESCRIPTION, "Live data streams via WebSocket and AMQP.");
        model.addAttribute(VIEW, "streams");
        return LAYOUT;
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute(TITLE, "Settings");
        model.addAttribute(VIEW, "settings");
        return LAYOUT;
    }
}

