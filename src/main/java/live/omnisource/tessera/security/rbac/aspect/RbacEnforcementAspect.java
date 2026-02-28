package live.omnisource.tessera.security.rbac.aspect;

import live.omnisource.tessera.security.rbac.TesseraAuthorizationService;
import live.omnisource.tessera.security.rbac.annotations.RequireGlobalPermission;
import live.omnisource.tessera.security.rbac.annotations.RequireWorkspacePermission;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Aspect
@Component
@Profile("oidc")
public class RbacEnforcementAspect {

    private final TesseraAuthorizationService authz;

    public RbacEnforcementAspect(TesseraAuthorizationService authz) {
        this.authz = authz;
    }

    @Around("@annotation(live.omnisource.tessera.security.rbac.annotations.RequireGlobalPermission) || " +
            "@within(live.omnisource.tessera.security.rbac.annotations.RequireGlobalPermission)")
    public Object enforceGlobal(ProceedingJoinPoint pjp) throws Throwable {
        final Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        final RequireGlobalPermission ann = findGlobalAnnotation(method, pjp.getTarget().getClass());
        if (ann == null) return pjp.proceed();

        final Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (!authz.hasGlobalPermission(a, ann.value())) {
            throw new AccessDeniedException("Missing permission: " + ann.value());
        }
        return pjp.proceed();
    }

    @Around("@annotation(live.omnisource.tessera.security.rbac.annotations.RequireWorkspacePermission) || " +
            "@within(live.omnisource.tessera.security.rbac.annotations.RequireWorkspacePermission)")
    public Object enforceWorkspace(ProceedingJoinPoint pjp) throws Throwable {
        final MethodSignature sig = (MethodSignature) pjp.getSignature();
        final Method method = sig.getMethod();

        final RequireWorkspacePermission ann = findWorkspaceAnnotation(method, pjp.getTarget().getClass());
        if (ann == null) return pjp.proceed();

        final Map<String, Object> args = argsByName(sig.getParameterNames(), pjp.getArgs());
        final Object wsRaw = args.get(ann.workspaceParam());
        final String workspace = (wsRaw == null) ? null : wsRaw.toString();

        final Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (!authz.hasWorkspacePermission(a, workspace, ann.value())) {
            throw new AccessDeniedException("Missing permission: " + ann.value() + " for workspace=" + workspace);
        }

        return pjp.proceed();
    }

    private static Map<String, Object> argsByName(String[] names, Object[] args) {
        final Map<String, Object> m = new HashMap<>();
        if (names == null || args == null) return m;
        for (int i = 0; i < Math.min(names.length, args.length); i++) {
            m.put(names[i], args[i]);
        }
        return m;
    }

    private static RequireGlobalPermission findGlobalAnnotation(Method m, Class<?> targetType) {
        RequireGlobalPermission a = m.getAnnotation(RequireGlobalPermission.class);
        if (a != null) return a;
        return targetType.getAnnotation(RequireGlobalPermission.class);
    }

    private static RequireWorkspacePermission findWorkspaceAnnotation(Method m, Class<?> targetType) {
        RequireWorkspacePermission a = m.getAnnotation(RequireWorkspacePermission.class);
        if (a != null) return a;
        return targetType.getAnnotation(RequireWorkspacePermission.class);
    }
}
