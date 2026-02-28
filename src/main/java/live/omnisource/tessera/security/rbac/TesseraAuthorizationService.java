package live.omnisource.tessera.security.rbac;

import live.omnisource.tessera.security.rbac.model.RbacSubjectBinding;
import live.omnisource.tessera.security.rbac.store.RbacPolicyStore;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service("tesseraAuthz")
@Profile("oidc")
public class TesseraAuthorizationService {

    private static final Duration RELOAD_EVERY = Duration.ofSeconds(5);

    private final RbacPolicyStore store;
    private final TesseraSubjectResolver subjectResolver;

    private volatile Instant lastLoad = Instant.EPOCH;
    private volatile Map<String, RbacSubjectBinding> bySubject = Map.of();

    public TesseraAuthorizationService(RbacPolicyStore store, TesseraSubjectResolver subjectResolver) {
        this.store = store;
        this.subjectResolver = subjectResolver;
    }

    public boolean hasGlobalPermission(Authentication auth, TesseraPermission permission) {
        refreshIfNeeded();
        if (permission == null) return false;

        final var roles = resolveGlobalRoles(auth);
        if (roles.contains(TesseraRole.TESSERA_PLATFORM_ADMIN)) return true;

        return rolesToPermissions(roles).contains(permission);
    }

    public boolean hasWorkspacePermission(Authentication auth, String workspace, TesseraPermission permission) {
        refreshIfNeeded();
        if (permission == null) return false;

        final var roles = resolveWorkspaceRoles(auth, workspace);
        if (roles.contains(TesseraRole.TESSERA_PLATFORM_ADMIN)) return true;

        return rolesToPermissions(roles).contains(permission);
    }

    public Set<TesseraRole> resolveGlobalRoles(Authentication auth) {
        refreshIfNeeded();
        final Set<TesseraRole> roles = new LinkedHashSet<>();

        for (String s : subjectResolver.resolveSubjects(auth)) {
            final RbacSubjectBinding b = bySubject.get(s);
            if (b == null) continue;
            roles.addAll(Optional.ofNullable(b.getGlobalRoles()).orElse(List.of()));
        }

        return roles;
    }

    public Set<TesseraRole> resolveWorkspaceRoles(Authentication auth, String workspace) {
        refreshIfNeeded();
        final Set<TesseraRole> roles = new LinkedHashSet<>();

        // global roles always apply
        roles.addAll(resolveGlobalRoles(auth));

        if (workspace == null || workspace.isBlank()) {
            return roles;
        }

        for (String s : subjectResolver.resolveSubjects(auth)) {
            final RbacSubjectBinding b = bySubject.get(s);
            if (b == null) continue;

            final var ws = b.getWorkspaceRoles();
            if (ws == null) continue;

            roles.addAll(Optional.ofNullable(ws.get(workspace)).orElse(List.of()));
        }

        return roles;
    }

    private Set<TesseraPermission> rolesToPermissions(Set<TesseraRole> roles) {
        final Set<TesseraPermission> out = new LinkedHashSet<>();
        for (TesseraRole r : roles) {
            out.addAll(BuiltInRolePermissions.ROLE_PERMISSIONS.getOrDefault(r, EnumSet.noneOf(TesseraPermission.class)));
        }
        return out;
    }

    private void refreshIfNeeded() {
        final Instant now = Instant.now();
        if (Duration.between(lastLoad, now).compareTo(RELOAD_EVERY) < 0) {
            return;
        }
        synchronized (this) {
            if (Duration.between(lastLoad, Instant.now()).compareTo(RELOAD_EVERY) < 0) {
                return;
            }
            this.bySubject = store.loadMergedBySubject();
            this.lastLoad = Instant.now();
        }
    }
}
