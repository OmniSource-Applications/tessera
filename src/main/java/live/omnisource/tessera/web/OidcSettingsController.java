package live.omnisource.tessera.web;

import live.omnisource.tessera.security.rbac.TesseraRole;
import live.omnisource.tessera.security.rbac.annotations.RequireGlobalRole;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * OIDC Settings UI pages.
 *
 * Only available when the "oidc" profile is active.
 */
@Controller
@Profile("oidc")
@RequireGlobalRole(TesseraRole.TESSERA_SECURITY_ADMIN)
@RequestMapping("/settings/oidc")
public class OidcSettingsController {

  private static final String LAYOUT = "layout/page";

  private final Environment env;

  public OidcSettingsController(Environment env) {
    this.env = env;
  }

  @GetMapping
  public String index(Model model) {
    model.addAttribute("title", "OIDC");
    model.addAttribute("description", "OIDC configuration and RBAC policy management.");
    model.addAttribute("view", "settings/oidc/index");

    model.addAttribute("issuerUri", env.getProperty("spring.security.oauth2.client.provider.tessera.issuer-uri"));
    model.addAttribute("clientId", env.getProperty("spring.security.oauth2.client.registration.tessera.client-id"));
    model.addAttribute("scopes", env.getProperty("spring.security.oauth2.client.registration.tessera.scope"));

    return LAYOUT;
  }
}