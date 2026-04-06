package live.omnisource.tessera.web;

import live.omnisource.tessera.datastore.DataStoreService;
import live.omnisource.tessera.security.rbac.TesseraRole;
import live.omnisource.tessera.security.rbac.annotations.RequireGlobalRole;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequireGlobalRole(TesseraRole.TESSERA_OPS_VIEWER)
@RequestMapping("/sources")
public class SourcesController {

  private final DataStoreService dataStoreService;

  public SourcesController(DataStoreService dataStoreService) {
    this.dataStoreService = dataStoreService;
  }

  @GetMapping
  public String list(Model model) {
    var sources = dataStoreService.listAllDataStores();
    model.addAttribute("title", "Sources");
    model.addAttribute("description", "All data stores across workspaces.");
    model.addAttribute("view", "sources/list");
    model.addAttribute("sources", sources);
    return "layout/page";
  }
}