package live.omnisource.tessera.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import live.omnisource.tessera.catalog.QueryCatalogService;
import live.omnisource.tessera.catalog.QueryExecutor;
import live.omnisource.tessera.catalog.QueryExecutor.QueryResult;
import live.omnisource.tessera.catalog.entity.CatalogEntry;
import live.omnisource.tessera.security.rbac.TesseraRole;
import live.omnisource.tessera.security.rbac.annotations.RequireGlobalRole;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/query")
public class QueryController {

  private final QueryCatalogService catalogService;
  private final QueryExecutor executor;
  private final ObjectMapper objectMapper;

  public QueryController(QueryCatalogService catalogService,
                         QueryExecutor executor,
                         ObjectMapper objectMapper) {
    this.catalogService = catalogService;
    this.executor = executor;
    this.objectMapper = objectMapper;
  }

  /**
   * Catalog browser — list all queries grouped by category.
   */
  @RequireGlobalRole(TesseraRole.TESSERA_OPS_VIEWER)
  @GetMapping
  public String index(@RequestParam(required = false) String category,
                      Model model) {
    var entries = category != null
            ? catalogService.listByCategory(category)
            : catalogService.listAll();

    // Group by category for the UI
    var grouped = new LinkedHashMap<String, List<CatalogEntry>>();
    for (var e : entries) {
      grouped.computeIfAbsent(e.getCategory(), k -> new ArrayList<>()).add(e);
    }

    model.addAttribute("title", "Query Catalog");
    model.addAttribute("description", "Browse and execute catalog queries");
    model.addAttribute("view", "query/index");
    model.addAttribute("grouped", grouped);
    model.addAttribute("totalCount", entries.size());
    model.addAttribute("selectedCategory", category);
    return "layout/page";
  }

  /**
   * Query detail — view SQL, params, and execute interactively.
   */
  @RequireGlobalRole(TesseraRole.TESSERA_OPS_VIEWER)
  @GetMapping("/{name}")
  public String detail(@PathVariable String name, Model model) {
    var entry = catalogService.findByName(name);
    if (entry.isEmpty()) return "redirect:/query";

    model.addAttribute("title", name);
    model.addAttribute("description", entry.get().getDescription());
    model.addAttribute("view", "query/detail");
    model.addAttribute("entry", entry.get());
    model.addAttribute("paramNames", extractParamNames(entry.get()));

    try {
      model.addAttribute("paramSchemaJson",
              objectMapper.writerWithDefaultPrettyPrinter()
                      .writeValueAsString(entry.get().getParamSchema()));
    } catch (Exception e) {
      model.addAttribute("paramSchemaJson", "{}");
    }

    return "layout/page";
  }

  /**
   * Execute a query from the UI form.
   */
  @RequireGlobalRole(TesseraRole.TESSERA_OPS_VIEWER)
  @PostMapping("/{name}/execute")
  public String execute(@PathVariable String name,
                        @RequestParam Map<String, String> allParams,
                        Model model) {
    var entry = catalogService.findByName(name);
    if (entry.isEmpty()) return "redirect:/query";

    // Strip Spring/form params, keep only query params
    var params = new HashMap<String, Object>();
    for (var e : allParams.entrySet()) {
      if (e.getKey().startsWith("_")) continue; // skip CSRF etc.
      if (e.getValue() != null && !e.getValue().isBlank()) {
        params.put(e.getKey(), e.getValue());
      }
    }

    QueryResult result = executor.execute(entry.get(), params);

    model.addAttribute("title", name);
    model.addAttribute("description", entry.get().getDescription());
    model.addAttribute("view", "query/detail");
    model.addAttribute("entry", entry.get());
    model.addAttribute("paramNames", extractParamNames(entry.get()));
    model.addAttribute("result", result);
    model.addAttribute("suppliedParams", params);

    try {
      model.addAttribute("paramSchemaJson",
              objectMapper.writerWithDefaultPrettyPrinter()
                      .writeValueAsString(entry.get().getParamSchema()));
    } catch (Exception e) {
      model.addAttribute("paramSchemaJson", "{}");
    }

    return "layout/page";
  }

  /**
   * Create a new catalog entry from the UI.
   */
  @RequireGlobalRole(TesseraRole.TESSERA_PLATFORM_ADMIN)
  @PostMapping("/create")
  public String create(@RequestParam String name,
                       @RequestParam String category,
                       @RequestParam String querySql,
                       @RequestParam(required = false) String description,
                       @RequestParam(required = false) String tags,
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
      catalogService.create(entry);
      redirect.addFlashAttribute("success", "Query '" + name + "' created.");
    } catch (Exception e) {
      redirect.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/query";
  }

  /**
   * Delete a catalog entry.
   */
  @RequireGlobalRole(TesseraRole.TESSERA_PLATFORM_ADMIN)
  @PostMapping("/{id}/delete")
  public String delete(@PathVariable UUID id, RedirectAttributes redirect) {
    try {
      catalogService.delete(id);
      redirect.addFlashAttribute("success", "Query deleted.");
    } catch (Exception e) {
      redirect.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/query";
  }

  @SuppressWarnings("unchecked")
  private List<String> extractParamNames(CatalogEntry entry) {
    if (entry.getParamSchema() == null) return List.of();
    var props = (Map<String, Object>) entry.getParamSchema().get("properties");
    if (props == null) return List.of();
    return new ArrayList<>(props.keySet());
  }
}