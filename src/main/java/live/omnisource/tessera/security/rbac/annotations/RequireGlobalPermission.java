package live.omnisource.tessera.security.rbac.annotations;

import java.lang.annotation.*;

import live.omnisource.tessera.security.rbac.TesseraPermission;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireGlobalPermission {
  TesseraPermission value();
}
