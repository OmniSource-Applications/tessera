package live.omnisource.tessera.web;

import live.omnisource.tessera.feed.FeedService;
import live.omnisource.tessera.security.rbac.TesseraRole;
import live.omnisource.tessera.security.rbac.annotations.RequireWorkspaceRole;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/workspaces/{workspace}/feeds")
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_VIEWER, workspaceParam = "workspace")
    @GetMapping
    public String list(@PathVariable String workspace, Model model) {
        model.addAttribute("title", "Live Feeds — " + workspace);
        model.addAttribute("description", "Live data ingestion feeds for workspace " + workspace);
        model.addAttribute("view", "feeds/index");
        model.addAttribute("workspace", workspace);
        model.addAttribute("feeds", feedService.listFeeds(workspace));
        model.addAttribute("statuses", feedService.allStatuses(workspace));
        model.addAttribute("activeFeedCount", feedService.activeFeedCount(workspace));
        return "layout/page";
    }

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_EDITOR, workspaceParam = "workspace")
    @GetMapping("/new")
    public String newFeed(@PathVariable String workspace, Model model) {
        model.addAttribute("title", "Create Feed — " + workspace);
        model.addAttribute("description", "Configure a new live data feed");
        model.addAttribute("view", "feeds/create");
        model.addAttribute("workspace", workspace);
        return "layout/page";
    }

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_VIEWER, workspaceParam = "workspace")
    @GetMapping("/{name}")
    public String detail(@PathVariable String workspace,
                         @PathVariable String name, Model model) {
        var config = feedService.getFeed(workspace, name);
        if (config.isEmpty()) return "redirect:/workspaces/" + workspace + "/feeds";

        model.addAttribute("title", name + " — " + workspace);
        model.addAttribute("description", "Feed detail and status");
        model.addAttribute("view", "feeds/detail");
        model.addAttribute("workspace", workspace);
        model.addAttribute("feed", config.get());
        model.addAttribute("status", feedService.getStatus(workspace, name));
        return "layout/page";
    }

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_SYNC_OPERATOR, workspaceParam = "workspace")
    @PostMapping("/{name}/start")
    public String start(@PathVariable String workspace,
                        @PathVariable String name, RedirectAttributes redirect) {
        try {
            feedService.startFeed(workspace, name);
            redirect.addFlashAttribute("success", "Feed '" + name + "' started.");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/workspaces/" + workspace + "/feeds/" + name;
    }

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_SYNC_OPERATOR, workspaceParam = "workspace")
    @PostMapping("/{name}/stop")
    public String stop(@PathVariable String workspace,
                       @PathVariable String name, RedirectAttributes redirect) {
        feedService.stopFeed(workspace, name);
        redirect.addFlashAttribute("success", "Feed '" + name + "' stopped.");
        return "redirect:/workspaces/" + workspace + "/feeds/" + name;
    }

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_SYNC_OPERATOR, workspaceParam = "workspace")
    @PostMapping("/{name}/test")
    public String test(@PathVariable String workspace,
                       @PathVariable String name, RedirectAttributes redirect) {
        try {
            String result = feedService.testFeed(workspace, name);
            redirect.addFlashAttribute("success", "Connection test: " + result);
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Connection test failed: " + e.getMessage());
        }
        return "redirect:/workspaces/" + workspace + "/feeds/" + name;
    }

    @RequireWorkspaceRole(value = TesseraRole.TESSERA_EDITOR, workspaceParam = "workspace")
    @PostMapping("/{name}/delete")
    public String delete(@PathVariable String workspace,
                         @PathVariable String name, RedirectAttributes redirect) {
        try {
            feedService.deleteFeed(workspace, name);
            redirect.addFlashAttribute("success", "Feed '" + name + "' deleted.");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/workspaces/" + workspace + "/feeds";
    }
}