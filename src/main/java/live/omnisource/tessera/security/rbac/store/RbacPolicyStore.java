package live.omnisource.tessera.security.rbac.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import live.omnisource.tessera.filestore.FileStoreLayout;
import live.omnisource.tessera.filestore.FileStoreService;
import live.omnisource.tessera.security.rbac.model.RbacPolicyDocument;
import live.omnisource.tessera.security.rbac.model.RbacSubjectBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
@Profile("oidc")
public class RbacPolicyStore {

    private static final Logger log = LoggerFactory.getLogger(RbacPolicyStore.class);

    private final FileStoreService fileStore;
    private final ObjectMapper mapper;

    public RbacPolicyStore(FileStoreService fileStore, ObjectMapper mapper) {
        this.fileStore = fileStore;
        this.mapper = mapper;
    }

    public Path rbacDir() {
        return fileStore.resolve(FileStoreLayout.RBAC);
    }

    /**
     * Loads and merges all *.json policy docs under data_dir/etc/auth/rbac.
     * If the same subject appears in multiple files, roles are unioned.
     */
    public Map<String, RbacSubjectBinding> loadMergedBySubject() {
        final Path dir = rbacDir();
        if (!Files.isDirectory(dir)) {
            return Map.of();
        }

        final Map<String, RbacSubjectBinding> merged = new HashMap<>();

        try (var s = Files.list(dir)) {
            s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".json"))
             .sorted(Comparator.comparing(p -> p.getFileName().toString()))
             .forEach(p -> mergeOne(p, merged));
        } catch (IOException e) {
            log.warn("RBAC: failed listing policy directory {}", dir, e);
            return Map.of();
        }

        return merged;
    }

    private void mergeOne(Path file, Map<String, RbacSubjectBinding> merged) {
        try {
            final byte[] bytes = Files.readAllBytes(file);
            final RbacPolicyDocument doc = mapper.readValue(bytes, RbacPolicyDocument.class);
            if (doc.getSubjects() == null) return;

            for (RbacSubjectBinding b : doc.getSubjects()) {
                if (b == null || b.getSubject() == null || b.getSubject().isBlank()) continue;

                merged.merge(
                        b.getSubject(),
                        b,
                        (a, c) -> {
                            // union global
                            final var g = new LinkedHashSet<>(Optional.ofNullable(a.getGlobalRoles()).orElse(List.of()));
                            g.addAll(Optional.ofNullable(c.getGlobalRoles()).orElse(List.of()));
                            a.setGlobalRoles(new ArrayList<>(g));

                            // union workspace roles
                            final Map<String, List<live.omnisource.tessera.security.rbac.TesseraRole>> ws = a.getWorkspaceRoles();
                            final Map<String, List<live.omnisource.tessera.security.rbac.TesseraRole>> add = c.getWorkspaceRoles();
                            if (add != null) {
                                for (var e : add.entrySet()) {
                                    final String workspace = e.getKey();
                                    final var roles = new LinkedHashSet<>(ws.getOrDefault(workspace, List.of()));
                                    roles.addAll(Optional.ofNullable(e.getValue()).orElse(List.of()));
                                    ws.put(workspace, new ArrayList<>(roles));
                                }
                            }
                            a.setWorkspaceRoles(ws);
                            return a;
                        }
                );
            }

            log.debug("RBAC: loaded {}", file);

        } catch (Exception e) {
            log.warn("RBAC: failed parsing {}", file, e);
        }
    }
}
