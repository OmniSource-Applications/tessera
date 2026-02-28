package live.omnisource.tessera.security.rbac;

public enum TesseraRole {

    // Global roles
    TESSERA_PLATFORM_ADMIN,
    TESSERA_SECURITY_ADMIN,
    TESSERA_OPS_VIEWER,

    // Workspace-scoped roles
    TESSERA_OWNER,
    TESSERA_EDITOR,
    TESSERA_VIEWER,
    TESSERA_SYNC_OPERATOR
}
