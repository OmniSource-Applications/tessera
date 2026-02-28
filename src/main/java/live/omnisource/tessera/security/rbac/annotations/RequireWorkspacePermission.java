package live.omnisource.tessera.security.rbac.annotations;

import live.omnisource.tessera.security.rbac.TesseraPermission;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireWorkspacePermission {

    TesseraPermission value();

    /**
     * Name of the method argument that contains the workspace identifier.
     * For controllers, prefer: workspace (matches /workspaces/{workspace}/...).
     */
    String workspaceParam() default "workspace";
}
