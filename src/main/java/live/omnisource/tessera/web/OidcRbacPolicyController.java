package live.omnisource.tessera.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import live.omnisource.tessera.filestore.FileStoreLayout;
import live.omnisource.tessera.filestore.FileStoreService;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * RBAC Policy UI.
 *
 * JSON policy documents stored under: data_dir/etc/auth/rbac/*.json
 */
@Controller
@Profile("oidc")
@RequestMapping("/security/oidc/rbac")

public class OidcRbacPolicyController {
    private static final String LAYOUT = "layout/page";

    private final FileStoreService fileStoreService;
    private final ObjectMapper objectMapper;

    public OidcRbacPolicyController(FileStoreService fileStoreService, ObjectMapper objectMapper) {
        this.fileStoreService = fileStoreService;
        this.objectMapper = objectMapper;
    }

    private Path rbacDir() {
        return fileStoreService.resolve(FileStoreLayout.RBAC);
    }

    private static boolean isJsonFile(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        return name.endsWith(".json");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TESSERA_RBAC_VIEW')")
    public String list(Model model) {
        Path dir = rbacDir();
        if (!Files.isDirectory(dir)) {
            model.addAttribute("policies", List.of());
        } else {
            try (Stream<Path> s = Files.list(dir)) {
                List<Map<String, Object>> policies = s
                        .filter(Files::isRegularFile)
                        .filter(OidcRbacPolicyController::isJsonFile)
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .map(p -> {
                            try {
                                return Map.<String, Object>of(
                                        "name", p.getFileName().toString(),
                                        "size", Files.size(p),
                                        "modified", Instant.ofEpochMilli(Files.getLastModifiedTime(p).toMillis())
                                );
                            } catch (IOException e) {
                                return Map.<String, Object>of(
                                        "name", p.getFileName().toString(),
                                        "size", -1L,
                                        "modified", null
                                );
                            }
                        })
                        .toList();

                model.addAttribute("policies", policies);
            } catch (IOException e) {
                model.addAttribute("policies", List.of());
                model.addAttribute("error", "Failed to list RBAC policy files: " + e.getMessage());
            }
        }

        model.addAttribute("title", "RBAC Policies");
        model.addAttribute("description", "View and edit JSON policy documents used for workspace RBAC.");
        model.addAttribute("view", "security/oidc/rbac/list");
        return LAYOUT;
    }

    @GetMapping("/{file}")
    @PreAuthorize("hasAuthority('TESSERA_RBAC_VIEW')")
    public String edit(@PathVariable("file") String file, Model model) {
        String safe = sanitizeFileName(file);
        Path target = rbacDir().resolve(safe).normalize();

        if (!target.startsWith(rbacDir())) {
            throw new IllegalArgumentException("Invalid file");
        }

        String raw = "";
        if (Files.exists(target) && Files.isRegularFile(target)) {
            try {
                raw = Files.readString(target, StandardCharsets.UTF_8);
                raw = prettyJson(raw);
            } catch (IOException e) {
                model.addAttribute("error", "Failed to read file: " + e.getMessage());
            }
        } else {
            model.addAttribute("info", "New policy file. Save to create it.");
        }

        model.addAttribute("title", "RBAC Policy · " + safe);
        model.addAttribute("description", "Edit a JSON RBAC policy file.");
        model.addAttribute("view", "security/oidc/rbac/edit");
        model.addAttribute("file", safe);
        model.addAttribute("json", raw);
        return LAYOUT;
    }

    @PostMapping("/{file}")
    @PreAuthorize("hasAuthority('TESSERA_RBAC_EDIT')")
    public String save(@PathVariable("file") String file,
                       @RequestParam("json") String json,
                       RedirectAttributes redirect) {
        String safe = sanitizeFileName(file);
        if (!safe.endsWith(".json")) {
            safe = safe + ".json";
        }

        try {
            JsonNode node = objectMapper.readTree(json);
            String pretty = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);

            Path target = rbacDir().resolve(safe).normalize();
            if (!target.startsWith(rbacDir())) {
                throw new IllegalArgumentException("Invalid file");
            }

            Files.createDirectories(target.getParent());
            fileStoreService.writeAtomic(target, pretty.getBytes(StandardCharsets.UTF_8));
            redirect.addFlashAttribute("success", "Saved " + safe);
            return "redirect:/security/oidc/rbac/" + safe;

        } catch (JsonProcessingException e) {
            redirect.addFlashAttribute("error", "Invalid JSON: " + e.getOriginalMessage());
            return "redirect:/security/oidc/rbac/" + safe;
        } catch (IOException e) {
            redirect.addFlashAttribute("error", "Failed to save: " + e.getMessage());
            return "redirect:/security/oidc/rbac/" + safe;
        }
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('TESSERA_RBAC_EDIT')")
    public String create(@RequestParam("name") String name) {
        String safe = sanitizeFileName(name);
        if (!safe.endsWith(".json")) {
            safe = safe + ".json";
        }
        return "redirect:/security/oidc/rbac/" + safe;
    }

    private String prettyJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception ignored) {
            return json;
        }
    }

    private static String sanitizeFileName(String name) {
        String cleaned = name == null ? "" : name.trim();
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (cleaned.isBlank()) {
            cleaned = "policy";
        }
        return cleaned;
    }
}
