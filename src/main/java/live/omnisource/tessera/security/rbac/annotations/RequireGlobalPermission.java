package live.omnisource.tessera.security.rbac.annotations;

import live.omnisource.tessera.security.rbac.TesseraPermission;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireGlobalPermission {
    TesseraPermission value();
}
