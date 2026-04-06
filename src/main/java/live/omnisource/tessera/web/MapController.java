package live.omnisource.tessera.web;

import live.omnisource.tessera.catalog.QueryCatalogService;
import live.omnisource.tessera.security.rbac.TesseraRole;
import live.omnisource.tessera.security.rbac.annotations.RequireGlobalRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@Controller
@RequireGlobalRole(TesseraRole.TESSERA_OPS_VIEWER)
@RequestMapping("/map")
public class MapController {

  private final JdbcTemplate jdbcTemplate;

  public MapController(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @GetMapping
  public String index(Model model, HttpServletRequest request) {
    // Load available sources for the filter dropdown
    List<Map<String, Object>> sources = List.of();
    try {
      jdbcTemplate.execute(
              """
                  REFRESH MATERIALIZED VIEW tessera.mv_h3_density_r7;
                  """
      );


      sources = jdbcTemplate.queryForList("""
                    SELECT id, name, source_type, is_active
                    FROM tessera.external_sources
                    WHERE is_active = true
                    ORDER BY name
                    """);
    } catch (Exception ignored) {}


    model.addAttribute("view", "map/index");
    model.addAttribute("sources", sources);
    model.addAttribute("contextPath", request.getContextPath());
    model.addAttribute("mapFullScreen", true);
    return "layout/page";
  }
}