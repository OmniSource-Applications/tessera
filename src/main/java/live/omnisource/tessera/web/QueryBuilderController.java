package live.omnisource.tessera.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import live.omnisource.tessera.catalog.QueryCatalogService;
import live.omnisource.tessera.catalog.QueryExecutor;
import live.omnisource.tessera.catalog.entity.CatalogEntry;
import live.omnisource.tessera.security.rbac.TesseraRole;
import live.omnisource.tessera.security.rbac.annotations.RequireGlobalRole;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

/**
 * Serves the visual query builder at /query/builder.
 *
 * <p>The builder UI is a single-page Thymeleaf template with JavaScript that
 * dynamically constructs SQL from visual inputs. The backend provides:
 * <ul>
 *   <li>The builder page itself</li>
 *   <li>A save-to-catalog action</li>
 *   <li>A test-execute action (reuses QueryExecutor)</li>
 * </ul>
 */
@Controller
@RequestMapping("/query/builder")
public class QueryBuilderController {

  private final QueryCatalogService catalogService;
  private final QueryExecutor executor;
  private final ObjectMapper objectMapper;

  public QueryBuilderController(QueryCatalogService catalogService,
                                QueryExecutor executor,
                                ObjectMapper objectMapper) {
    this.catalogService = catalogService;
    this.executor = executor;
    this.objectMapper = objectMapper;
  }

  /**
   * GET /query/builder — show the visual query builder page.
   */
  @RequireGlobalRole(TesseraRole.TESSERA_OPS_VIEWER)
  @GetMapping
  public String builder(Model model) {
    model.addAttribute("title", "Query Builder");
    model.addAttribute("description", "Visually construct queries and save to catalog");
    model.addAttribute("view", "query/builder");
    return "layout/page";
  }

  /**
   * POST /query/builder/save — save a builder-composed query to the catalog.
   */
  @RequireGlobalRole(TesseraRole.TESSERA_PLATFORM_ADMIN)
  @PostMapping("/save")
  public String save(@RequestParam String name,
                     @RequestParam String category,
                     @RequestParam String querySql,
                     @RequestParam(required = false) String description,
                     @RequestParam(required = false) String tags,
                     @RequestParam(required = false) String paramSchemaJson,
                     @RequestParam(defaultValue = "30000") int timeoutMs,
                     @RequestParam(defaultValue = "false") boolean streaming,
                     RedirectAttributes redirect) {
    try {
      var entry = new CatalogEntry();
      entry.setName(name);
      entry.setCategory(category);
      entry.setQuerySql(querySql);
      entry.setDescription(description);
      entry.setTimeoutMs(timeoutMs);
      entry.setStreaming(streaming);

      if (tags != null && !tags.isBlank()) {
        entry.setTags(Arrays.asList(tags.split("\\s*,\\s*")));
      }

      if (paramSchemaJson != null && !paramSchemaJson.isBlank()) {
        @SuppressWarnings("unchecked")
        var schema = objectMapper.readValue(paramSchemaJson, Map.class);
        entry.setParamSchema(schema);
      }

      catalogService.create(entry);
      redirect.addFlashAttribute("success",
              "Query '" + name + "' saved to catalog.");
      return "redirect:/query/" + name;
    } catch (Exception e) {
      redirect.addFlashAttribute("error", e.getMessage());
      return "redirect:/query/builder";
    }
  }
}