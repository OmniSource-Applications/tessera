package live.omnisource.tessera.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import live.omnisource.tessera.apikey.ApiKeyFeatureToggle;

/**
 * Injects common model attributes available to all Thymeleaf templates. (#request is deprecated in
 * Thymeleaf 3.1, so we pass the path explicitly.)
 */
@ControllerAdvice
public class WebControllerAdvice {

  private final ApiKeyFeatureToggle apiKeyFeatureToggle;

  public WebControllerAdvice(ApiKeyFeatureToggle apiKeyFeatureToggle) {
    this.apiKeyFeatureToggle = apiKeyFeatureToggle;
  }

  @ModelAttribute("currentPath")
  public String currentPath(HttpServletRequest request) {
    String ctx = request.getContextPath(); // "/tessera"
    String uri = request.getRequestURI(); // "/tessera/workspaces"
    // Strip context path so sidebar matches on "/workspaces" etc.
    return uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
  }

  @ModelAttribute("apiKeysEnabled")
  public boolean apiKeysEnabled() {
    return apiKeyFeatureToggle.isEnabled();
  }
}
