package live.omnisource.tessera.security.rbac;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Component
public class TesseraSubjectResolver {

    public Set<String> resolveSubjects(Authentication auth) {
        if (auth == null) return Set.of();

        final Set<String> subjects = new LinkedHashSet<>();

        // Prefer JWT (resource server) if present
        if (auth instanceof JwtAuthenticationToken jat) {
            Jwt jwt = jat.getToken();
            addClaimSubjects(subjects, jwt.getClaims());
            return subjects;
        }

        // OAuth2 login (OIDC)
        if (auth instanceof OAuth2AuthenticationToken oat && oat.getPrincipal() instanceof OidcUser ou) {
            addClaimSubjects(subjects, ou.getClaims());
            return subjects;
        }

        // Fallback (non-oidc): use name
        subjects.add("name:" + auth.getName());
        return subjects;
    }

    private void addClaimSubjects(Set<String> out, java.util.Map<String, Object> claims) {
        if (claims == null) return;

        // sub
        Optional.ofNullable(claims.get("sub"))
                .map(Object::toString)
                .filter(s -> !s.isBlank())
                .ifPresent(sub -> out.add("oidc:sub:" + sub));

        // preferred_username
        Optional.ofNullable(claims.get("preferred_username"))
                .map(Object::toString)
                .filter(s -> !s.isBlank())
                .ifPresent(u -> out.add("oidc:username:" + u));

        // email
        Optional.ofNullable(claims.get("email"))
                .map(Object::toString)
                .filter(s -> !s.isBlank())
                .ifPresent(e -> out.add("oidc:email:" + e));
    }
}
