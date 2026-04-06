package live.omnisource.tessera.web;

import live.omnisource.tessera.layer.LayerService;
import live.omnisource.tessera.layer.dto.LayerDto;
import live.omnisource.tessera.layergroup.LayerGroupService;
import live.omnisource.tessera.security.rbac.TesseraRole;
import live.omnisource.tessera.security.rbac.annotations.RequireGlobalRole;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/layergroups")
public class LayerGroupController {

  private final LayerGroupService layerGroupService;
  private final LayerService layerService;

  public LayerGroupController(LayerGroupService layerGroupService,
                              LayerService layerService) {
    this.layerGroupService = layerGroupService;
    this.layerService = layerService;
  }

  /**
   * List all layer groups.
   */
  @RequireGlobalRole(TesseraRole.TESSERA_OPS_VIEWER)
  @GetMapping
  public String list(Model model) {
    model.addAttribute("title", "Layer Groups");
    model.addAttribute("description", "Virtual layer collections for combined data visualization.");
    model.addAttribute("view", "groups/index");
    model.addAttribute("groups", layerGroupService.listGroups());
    return "layout/page";
  }

  /**
   * Create a new group.
   */
  @RequireGlobalRole(TesseraRole.TESSERA_PLATFORM_ADMIN)
  @PostMapping
  public String create(@RequestParam("name") String name,
                       @RequestParam(value = "description", defaultValue = "") String description,
                       RedirectAttributes redirect) {
    try {
      layerGroupService.createGroup(name, description);
      redirect.addFlashAttribute("success", "Layer group '" + name + "' created.");
    } catch (Exception e) {
      redirect.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/layergroups";
  }

  /**
   * Group detail — shows members and add/remove controls.
   */
  @RequireGlobalRole(TesseraRole.TESSERA_OPS_VIEWER)
  @GetMapping("/{name}")
  public String detail(@PathVariable String name, Model model) {
    var group = layerGroupService.getGroup(name);
    var allLayers = layerService.listAllLayers();

    // Filter out layers already in the group
    var existingPaths = group.members().stream()
            .map(m -> m.workspace() + "/" + m.datastore() + "/" + m.layer())
            .toList();
    var availableLayers = allLayers.stream()
            .filter(l -> !existingPaths.contains(l.workspace() + "/" + l.datastore() + "/" + l.layer()))
            .toList();

    model.addAttribute("title", group.name());
    model.addAttribute("description", "Layer group details and member management.");
    model.addAttribute("view", "groups/detail");
    model.addAttribute("group", group);
    model.addAttribute("availableLayers", availableLayers);

    return "layout/page";
  }

  /**
   * Delete a group.
   */
  @RequireGlobalRole(TesseraRole.TESSERA_PLATFORM_ADMIN)
  @PostMapping("/{name}/delete")
  public String delete(@PathVariable String name, RedirectAttributes redirect) {
    try {
      layerGroupService.deleteGroup(name);
      redirect.addFlashAttribute("success", "Layer group '" + name + "' deleted.");
    } catch (Exception e) {
      redirect.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/layergroups";
  }

  /**
   * Toggle active state.
   */
  @RequireGlobalRole(TesseraRole.TESSERA_PLATFORM_ADMIN)
  @PostMapping("/{name}/toggle")
  public String toggle(@PathVariable String name,
                       @RequestParam("active") boolean active,
                       RedirectAttributes redirect) {
    try {
      layerGroupService.toggleActive(name, active);
      redirect.addFlashAttribute("success",
              "Layer group '" + name + "' " + (active ? "activated" : "deactivated") + ".");
    } catch (Exception e) {
      redirect.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/layergroups/" + name;
  }

  /**
   * Add a member layer. The select value is "workspace/datastore/layer".
   */
  @RequireGlobalRole(TesseraRole.TESSERA_PLATFORM_ADMIN)
  @PostMapping("/{name}/members")
  public String addMember(@PathVariable String name,
                          @RequestParam("layerPath") String layerPath,
                          RedirectAttributes redirect) {
    try {
      String[] parts = layerPath.split("/", 3);
      if (parts.length != 3) {
        throw new IllegalArgumentException("Invalid layer path: " + layerPath);
      }
      layerGroupService.addMember(name, new LayerDto(parts[0], parts[1], parts[2]));
      redirect.addFlashAttribute("success", "Layer '" + layerPath + "' added to group.");
    } catch (Exception e) {
      redirect.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/layergroups/" + name;
  }

  /**
   * Remove a member layer.
   */
  @RequireGlobalRole(TesseraRole.TESSERA_PLATFORM_ADMIN)
  @PostMapping("/{name}/members/remove")
  public String removeMember(@PathVariable String name,
                             @RequestParam("workspace") String workspace,
                             @RequestParam("datastore") String datastore,
                             @RequestParam("layer") String layer,
                             RedirectAttributes redirect) {
    try {
      layerGroupService.removeMember(name, workspace, datastore, layer);
      redirect.addFlashAttribute("success",
              "Layer '" + workspace + "/" + datastore + "/" + layer + "' removed from group.");
    } catch (Exception e) {
      redirect.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/layergroups/" + name;
  }
}