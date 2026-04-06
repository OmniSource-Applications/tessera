package live.omnisource.tessera.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import live.omnisource.tessera.filestore.FileStoreLayout;
import live.omnisource.tessera.filestore.FileStoreService;
import live.omnisource.tessera.security.rbac.BuiltInRolePermissions;
import live.omnisource.tessera.security.rbac.TesseraPermission;
import live.omnisource.tessera.security.rbac.TesseraRole;
import live.omnisource.tessera.security.rbac.annotations.RequireGlobalRole;
import live.omnisource.tessera.workspace.WorkspaceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * Visual RBAC policy editor.
 *
 * <p>Works in ALL profiles (not just OIDC) so policies can be prepared before
 * switching to OIDC authentication. Stores JSON policy documents under
 * {@code data_dir/etc/auth/rbac/*.json}.</p>
 *
 * <p>Replaces the previous OidcRbacPolicyController for UI purposes. That
 * controller can be kept for backwards compatibility but this one takes over
 * the routing.</p>
 */
@Controller
@RequireGlobalRole(TesseraRole.TESSERA_SECURITY_ADMIN)
@RequestMapping("/settings/rbac")
public class RbacController {

    private static final String LAYOUT = "layout/page";

    private final FileStoreService fileStoreService;
    private final ObjectMapper objectMapper;
    private final WorkspaceService workspaceService;

    public RbacController(FileStoreService fileStoreService,
                          ObjectMapper objectMapper,
                          WorkspaceService workspaceService) {
        this.fileStoreService = fileStoreService;
        this.objectMapper = objectMapper;
        this.workspaceService = workspaceService;
    }

    private Path rbacDir() {
        return fileStoreService.resolve(FileStoreLayout.RBAC);
    }

    // ── List ────────────────────────────────────────────

    @GetMapping
    public String list(Model model) {
        Path dir = rbacDir();
        List<Map<String, Object>> policies = List.of();

        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                policies = s
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".json"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .map(p -> {
                            try {
                                return Map.<String, Object>of(
                                        "name", p.getFileName().toString(),
                                        "size", Files.size(p),
                                        "modified", Instant.ofEpochMilli(
                                                Files.getLastModifiedTime(p).toMillis()).toString().substring(0, 16)
                                );
                            } catch (IOException e) {
                                return Map.<String, Object>of("name", p.getFileName().toString(),
                                        "size", -1L, "modified", "—");
                            }
                        })
                        .toList();
            } catch (IOException e) {
                model.addAttribute("error", "Failed to list RBAC policy files: " + e.getMessage());
            }
        }

        model.addAttribute("title", "RBAC Policies");
        model.addAttribute("description", "Manage role-based access control policies");
        model.addAttribute("view", "settings/rbac/list");
        model.addAttribute("policies", policies);
        return LAYOUT;
    }

    // ── Visual Editor ──────────────────────────────────

    @GetMapping("/{file}")
    public String edit(@PathVariable("file") String file, Model model) {
        String safe = sanitize(file);
        Path target = rbacDir().resolve(safe).normalize();

        if (!target.startsWith(rbacDir())) {
            throw new IllegalArgumentException("Invalid file path");
        }

        String json = "";
        if (Files.exists(target) && Files.isRegularFile(target)) {
            try {
                json = Files.readString(target, StandardCharsets.UTF_8);
                json = prettyJson(json);
            } catch (IOException e) {
                model.addAttribute("error", "Failed to read file: " + e.getMessage());
            }
        } else {
            // Seed a blank policy template for new files
            json = prettyJson("""
                    {"schemaVersion":1,"subjects":[]}
                    """);
            model.addAttribute("isNew", true);
        }

        // Pass metadata for the visual editor
        model.addAttribute("title", "RBAC Policy · " + safe);
        model.addAttribute("description", "Edit role and permission assignments");
        model.addAttribute("view", "settings/rbac/editor");
        model.addAttribute("file", safe);
        model.addAttribute("json", json);
        model.addAttribute("allRoles", Arrays.stream(TesseraRole.values()).map(Enum::name).toList());
        model.addAttribute("globalRoles", List.of(
                "TESSERA_PLATFORM_ADMIN", "TESSERA_SECURITY_ADMIN", "TESSERA_OPS_VIEWER"
        ));
        model.addAttribute("workspaceRoles", List.of(
                "TESSERA_OWNER", "TESSERA_EDITOR", "TESSERA_VIEWER", "TESSERA_SYNC_OPERATOR"
        ));
        model.addAttribute("allPermissions", Arrays.stream(TesseraPermission.values()).map(Enum::name).toList());
        model.addAttribute("workspaces", workspaceService.listWorkspaces());

        // Build role → permissions map for the UI reference panel
        var rolePermsMap = new LinkedHashMap<String, List<String>>();
        BuiltInRolePermissions.ROLE_PERMISSIONS.forEach((role, perms) ->
                rolePermsMap.put(role.name(), perms.stream().map(Enum::name).sorted().toList()));
        model.addAttribute("rolePermissions", rolePermsMap);

        return LAYOUT;
    }

    @PostMapping("/{file}")
    public String save(@PathVariable("file") String file,
                       @RequestParam("json") String json,
                       RedirectAttributes redirect) {
        String safe = sanitize(file);
        if (!safe.endsWith(".json")) safe += ".json";

        try {
            // Validate JSON structure
            JsonNode node = objectMapper.readTree(json);
            String pretty = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);

            Path target = rbacDir().resolve(safe).normalize();
            if (!target.startsWith(rbacDir())) {
                throw new IllegalArgumentException("Invalid file path");
            }

            Files.createDirectories(target.getParent());
            fileStoreService.writeAtomic(target, pretty.getBytes(StandardCharsets.UTF_8));
            redirect.addFlashAttribute("success", "Saved " + safe);
            return "redirect:/settings/rbac/" + safe;

        } catch (JsonProcessingException e) {
            redirect.addFlashAttribute("error", "Invalid JSON: " + e.getOriginalMessage());
        } catch (IOException e) {
            redirect.addFlashAttribute("error", "Failed to save: " + e.getMessage());
        }
        return "redirect:/settings/rbac/" + safe;
    }

    @PostMapping("/new")
    public String create(@RequestParam("name") String name) {
        String safe = sanitize(name);
        if (!safe.endsWith(".json")) safe += ".json";
        return "redirect:/settings/rbac/" + safe;
    }

    @PostMapping("/{file}/delete")
    public String delete(@PathVariable("file") String file, RedirectAttributes redirect) {
        String safe = sanitize(file);
        Path target = rbacDir().resolve(safe).normalize();
        if (!target.startsWith(rbacDir())) {
            redirect.addFlashAttribute("error", "Invalid file path");
            return "redirect:/settings/rbac";
        }
        try {
            Files.deleteIfExists(target);
            redirect.addFlashAttribute("success", "Deleted " + safe);
        } catch (IOException e) {
            redirect.addFlashAttribute("error", "Failed to delete: " + e.getMessage());
        }
        return "redirect:/settings/rbac";
    }

    // ── Helpers ─────────────────────────────────────────

    private String prettyJson(String json) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(objectMapper.readTree(json));
        } catch (Exception e) {
            return json;
        }
    }

    private static String sanitize(String name) {
        String cleaned = (name == null ? "" : name.trim())
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned.isBlank() ? "policy" : cleaned;
    }
}