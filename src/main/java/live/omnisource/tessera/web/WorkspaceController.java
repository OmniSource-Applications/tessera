package live.omnisource.tessera.web;

import live.omnisource.tessera.datastore.DataStoreService;
import live.omnisource.tessera.workspace.WorkspaceService;
import live.omnisource.tessera.workspace.dto.WorkspaceDto;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final DataStoreService dataStoreService;

    public WorkspaceController(WorkspaceService workspaceService,
                               DataStoreService dataStoreService) {
        this.workspaceService = workspaceService;
        this.dataStoreService = dataStoreService;
    }

    /**
     * List all workspaces.
     */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("title", "Workspaces");
        model.addAttribute("description", "Manage your project workspaces.");
        model.addAttribute("view", "workspaces/list");
        model.addAttribute("workspaces", workspaceService.listWorkspaces());
        return "layout/page";
    }

    /**
     * Create a new workspace.
     */
    @PostMapping
    public String create(@RequestParam("name") String name, RedirectAttributes redirect) {
        try {
            workspaceService.createWorkspace(new WorkspaceDto(name));
            redirect.addFlashAttribute("success", "Workspace '" + name + "' created.");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/workspaces";
    }

    /**
     * Delete a workspace.
     */
    @PostMapping("/{name}/delete")
    public String delete(@PathVariable String name, RedirectAttributes redirect) {
        try {
            workspaceService.deleteWorkspace(new WorkspaceDto(name));
            redirect.addFlashAttribute("success", "Workspace '" + name + "' deleted.");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/workspaces";
    }

    /**
     * Workspace detail — shows data stores and create form.
     */
    @GetMapping("/{name}")
    public String detail(@PathVariable String name, Model model) {
        var workspace = workspaceService.getWorkspace(new WorkspaceDto(name));
        var datastores = dataStoreService.listDataStores(name);

        model.addAttribute("title", name);
        model.addAttribute("description", "Workspace details and data stores.");
        model.addAttribute("view", "workspaces/detail");
        model.addAttribute("workspace", workspace);
        model.addAttribute("datastores", datastores);
        return "layout/page";
    }
}
