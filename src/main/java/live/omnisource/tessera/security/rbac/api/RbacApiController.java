package live.omnisource.tessera.security.rbac.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import live.omnisource.tessera.filestore.FileStoreLayout;
import live.omnisource.tessera.filestore.FileStoreService;
import live.omnisource.tessera.security.rbac.BuiltInRolePermissions;
import live.omnisource.tessera.security.rbac.TesseraPermission;
import live.omnisource.tessera.security.rbac.TesseraRole;
import live.omnisource.tessera.security.rbac.annotations.RequireGlobalRole;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

@RestController
@RequireGlobalRole(TesseraRole.TESSERA_SECURITY_ADMIN)
@RequestMapping("/api/rbac")
public class RbacApiController {

    private final FileStoreService fileStore;
    private final ObjectMapper objectMapper;

    public RbacApiController(FileStoreService fileStore, ObjectMapper objectMapper) {
        this.fileStore = fileStore;
        this.objectMapper = objectMapper;
    }

    // ── Policy file CRUD ────────────────────────────────

    @GetMapping("/policies")
    public ResponseEntity<List<Map<String, Object>>> listPolicies() {
        Path dir = rbacDir();
        if (!Files.isDirectory(dir)) return ResponseEntity.ok(List.of());

        try (Stream<Path> files = Files.list(dir)) {
            var result = files.filter(p -> p.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .map(p -> {
                        try {
                            return Map.<String, Object>of(
                                    "name", p.getFileName().toString(),
                                    "size", Files.size(p),
                                    "modified", Files.getLastModifiedTime(p).toInstant().toString()
                            );
                        } catch (IOException e) {
                            return Map.<String, Object>of("name", p.getFileName().toString());
                        }
                    })
                    .toList();
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/policies/{file}")
    public ResponseEntity<?> getPolicy(@PathVariable String file) {
        Path target = rbacDir().resolve(sanitize(file)).normalize();
        if (!target.startsWith(rbacDir()) || !Files.exists(target)) {
            return ResponseEntity.notFound().build();
        }
        try {
            String content = Files.readString(target, StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(content);
            return ResponseEntity.ok(node);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/policies/{file}")
    public ResponseEntity<?> savePolicy(@PathVariable String file,
                                        @RequestBody String json) {
        String safe = sanitize(file);
        if (!safe.endsWith(".json")) safe += ".json";

        try {
            JsonNode node = objectMapper.readTree(json);
            String pretty = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);

            Path target = rbacDir().resolve(safe).normalize();
            if (!target.startsWith(rbacDir())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid file path"));
            }

            Files.createDirectories(target.getParent());
            fileStore.writeAtomic(target, pretty.getBytes(StandardCharsets.UTF_8));

            return ResponseEntity.ok(Map.of("name", safe, "saved", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/policies/{file}")
    public ResponseEntity<Void> deletePolicy(@PathVariable String file) {
        Path target = rbacDir().resolve(sanitize(file)).normalize();
        if (!target.startsWith(rbacDir())) return ResponseEntity.badRequest().build();

        try {
            Files.deleteIfExists(target);
            return ResponseEntity.noContent().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Reference endpoints ─────────────────────────────

    @GetMapping("/roles")
    public List<String> listRoles() {
        return Arrays.stream(TesseraRole.values()).map(Enum::name).toList();
    }

    @GetMapping("/permissions")
    public List<String> listPermissions() {
        return Arrays.stream(TesseraPermission.values()).map(Enum::name).toList();
    }

    @GetMapping("/roles/{role}/permissions")
    public ResponseEntity<?> rolePermissions(@PathVariable String role) {
        try {
            TesseraRole r = TesseraRole.valueOf(role);
            var perms = BuiltInRolePermissions.ROLE_PERMISSIONS.getOrDefault(r, EnumSet.noneOf(TesseraPermission.class));
            return ResponseEntity.ok(Map.of(
                    "role", role,
                    "permissions", perms.stream().map(Enum::name).sorted().toList()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Helpers ─────────────────────────────────────────

    private Path rbacDir() {
        return fileStore.resolve(FileStoreLayout.RBAC);
    }

    private static String sanitize(String name) {
        return (name == null ? "" : name.trim()).replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}