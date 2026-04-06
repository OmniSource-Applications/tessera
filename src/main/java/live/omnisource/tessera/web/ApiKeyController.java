package live.omnisource.tessera.web;

import live.omnisource.tessera.apikey.ApiKeyFeatureToggle;
import live.omnisource.tessera.apikey.ApiKeyService;
import live.omnisource.tessera.security.rbac.TesseraRole;
import live.omnisource.tessera.security.rbac.annotations.RequireGlobalRole;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Controller
@RequireGlobalRole(TesseraRole.TESSERA_SECURITY_ADMIN)
@RequestMapping("/settings/api-keys")
public class ApiKeyController {

  private final ApiKeyService apiKeyService;
  private final ApiKeyFeatureToggle featureToggle;

  public ApiKeyController(ApiKeyService apiKeyService, ApiKeyFeatureToggle featureToggle) {
    this.apiKeyService = apiKeyService;
    this.featureToggle = featureToggle;
  }

  @GetMapping
  public String index(Model model) {
    model.addAttribute("title", "API Keys");
    model.addAttribute("description", "Manage API keys for programmatic access");
    model.addAttribute("view", "settings/apikeys/index");
    model.addAttribute("apiKeysEnabled", featureToggle.isEnabled());

    if (featureToggle.isEnabled()) {
      model.addAttribute("keys", apiKeyService.listAll());
      model.addAttribute("allScopes", ApiKeyService.ALL_SCOPES.stream().sorted().toList());
    }

    return "layout/page";
  }

  @PostMapping("/toggle")
  public String toggle(RedirectAttributes redirect) {
    featureToggle.toggle();
    String state = featureToggle.isEnabled() ? "enabled" : "disabled";
    redirect.addFlashAttribute("success", "API key authentication " + state + ".");
    return "redirect:/settings/api-keys";
  }

  @PostMapping("/create")
  public String create(@RequestParam String name,
                       @RequestParam(required = false) String scopes,
                       @RequestParam(defaultValue = "60") int rateLimitRpm,
                       @RequestParam(required = false) Integer expiresInDays,
                       Principal principal,
                       RedirectAttributes redirect) {
    if (!featureToggle.isEnabled()) {
      redirect.addFlashAttribute("error", "API keys are disabled. Enable them first.");
      return "redirect:/settings/api-keys";
    }

    try {
      List<String> scopeList = null;
      if (scopes != null && !scopes.isBlank()) {
        scopeList = Arrays.asList(scopes.split("\\s*,\\s*"));
      }

      Instant expiresAt = null;
      if (expiresInDays != null && expiresInDays > 0) {
        expiresAt = Instant.now().plus(expiresInDays, ChronoUnit.DAYS);
      }

      var result = apiKeyService.create(name, principal.getName(), scopeList, rateLimitRpm, expiresAt);

      redirect.addFlashAttribute("createdKey", result.rawKey());
      redirect.addFlashAttribute("createdKeyName", result.entity().getName());
      redirect.addFlashAttribute("success",
              "API key '" + name + "' created. Copy the key below — it won't be shown again.");
    } catch (Exception e) {
      redirect.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/settings/api-keys";
  }

  @PostMapping("/{id}/revoke")
  public String revoke(@PathVariable UUID id, RedirectAttributes redirect) {
    try {
      apiKeyService.revoke(id);
      redirect.addFlashAttribute("success", "API key revoked.");
    } catch (Exception e) {
      redirect.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/settings/api-keys";
  }

  @PostMapping("/{id}/delete")
  public String delete(@PathVariable UUID id, RedirectAttributes redirect) {
    try {
      apiKeyService.delete(id);
      redirect.addFlashAttribute("success", "API key deleted permanently.");
    } catch (Exception e) {
      redirect.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/settings/api-keys";
  }
}