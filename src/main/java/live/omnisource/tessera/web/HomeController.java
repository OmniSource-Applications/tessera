package live.omnisource.tessera.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    /**
     * Every authenticated page follows this pattern:
     *   1. Set "title"       → shows in <head> and topbar
     *   2. Set "view"        → template name containing th:fragment="content"
     *   3. Set "description" → (optional) renders page header under the topbar
     *   4. Return "layout/page"
     */
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("title", "Dashboard");
        model.addAttribute("description", "Overview of indexed data sources and live streams.");
        model.addAttribute("view", "home");
        return "layout/page";
    }

    @GetMapping("/sources")
    public String sources(Model model) {
        model.addAttribute("title", "Sources");
        model.addAttribute("description", "Manage your connected data sources.");
        model.addAttribute("view", "sources");
        return "layout/page";
    }

    @GetMapping("/query")
    public String query(Model model) {
        model.addAttribute("title", "Query");
        model.addAttribute("view", "query");  // no description → no page header
        return "layout/page";
    }

    @GetMapping("/streams")
    public String streams(Model model) {
        model.addAttribute("title", "Streams");
        model.addAttribute("description", "Live data streams via WebSocket and AMQP.");
        model.addAttribute("view", "streams");
        return "layout/page";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("title", "Settings");
        model.addAttribute("view", "settings");
        return "layout/page";
    }
}

