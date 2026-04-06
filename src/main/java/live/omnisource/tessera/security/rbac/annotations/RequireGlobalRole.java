package live.omnisource.tessera.security.rbac.annotations;

import live.omnisource.tessera.security.rbac.TesseraRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireGlobalRole {
    TesseraRole value();

    boolean includeHigher() default true;
}
