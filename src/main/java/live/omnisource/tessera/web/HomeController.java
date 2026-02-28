package live.omnisource.tessera.web;

import live.omnisource.tessera.datastore.DataStoreService;
import live.omnisource.tessera.layer.LayerService;
import live.omnisource.tessera.workspace.WorkspaceService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Arrays;

@Controller
public class HomeController {

    private static final String LAYOUT = "layout/page";
    private static final String TITLE = "title";
    private static final String DESCRIPTION = "description";
    private static final String VIEW = "view";

    private final WorkspaceService workspaceService;
    private final DataStoreService dataStoreService;
    private final LayerService layerService;
    private final Environment environment;

    public HomeController(WorkspaceService workspaceService,
                          DataStoreService dataStoreService,
                          LayerService layerService,
                          Environment environment) {
        this.workspaceService = workspaceService;
        this.dataStoreService = dataStoreService;
        this.layerService = layerService;
        this.environment = environment;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("title", "Dashboard");
        model.addAttribute("description", "Overview of indexed data sources and live streams.");
        model.addAttribute("view", "home");
        model.addAttribute("workspacesCount", workspaceService.countWorkspaces());
        model.addAttribute("sourcesCount", dataStoreService.countAllDataStores());
        model.addAttribute("layersCount", layerService.countAllLayers());

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

    @GetMapping("/security")
    public String security(Model model) {
        model.addAttribute(TITLE, "Security");
        model.addAttribute(VIEW, "security");
        model.addAttribute("activeProfiles", Arrays.asList(environment.getActiveProfiles()));
        model.addAttribute("oidcEnabled", Arrays.asList(environment.getActiveProfiles()).contains("oidc"));
        return LAYOUT;
    }
}

