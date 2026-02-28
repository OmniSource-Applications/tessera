package live.omnisource.tessera.security.rbac;

import java.util.EnumSet;
import java.util.Map;

import static live.omnisource.tessera.security.rbac.TesseraPermission.*;

public final class BuiltInRolePermissions {

    private BuiltInRolePermissions() {}

    public static final Map<TesseraRole, EnumSet<TesseraPermission>> ROLE_PERMISSIONS = Map.of(
            // Global roles
            TesseraRole.TESSERA_PLATFORM_ADMIN, EnumSet.allOf(TesseraPermission.class),

            TesseraRole.TESSERA_SECURITY_ADMIN, EnumSet.of(
                    TESSERA_RBAC_VIEW, TESSERA_RBAC_EDIT,
                    TESSERA_OPS_VIEW // optional; remove if desired
            ),

            TesseraRole.TESSERA_OPS_VIEWER, EnumSet.of(
                    TESSERA_OPS_VIEW
            ),

            // Workspace roles
            TesseraRole.TESSERA_OWNER, EnumSet.of(
                    TESSERA_WORKSPACE_LIST, TESSERA_WORKSPACE_VIEW,
                    TESSERA_WORKSPACE_DELETE,
                    TESSERA_WORKSPACE_MANAGE_MEMBERS,

                    TESSERA_DATASTORE_LIST, TESSERA_DATASTORE_VIEW,
                    TESSERA_DATASTORE_CREATE, TESSERA_DATASTORE_UPDATE, TESSERA_DATASTORE_DELETE,
                    TESSERA_DATASTORE_TEST_CONNECTION,

                    TESSERA_LAYER_LIST, TESSERA_LAYER_VIEW,
                    TESSERA_LAYER_CREATE, TESSERA_LAYER_UPDATE, TESSERA_LAYER_DELETE,
                    TESSERA_LAYER_INTROSPECT,

                    TESSERA_SYNC_VIEW, TESSERA_SYNC_RUN, TESSERA_SYNC_CONFIGURE
            ),

            TesseraRole.TESSERA_EDITOR, EnumSet.of(
                    TESSERA_WORKSPACE_LIST, TESSERA_WORKSPACE_VIEW,

                    TESSERA_DATASTORE_LIST, TESSERA_DATASTORE_VIEW,
                    TESSERA_DATASTORE_CREATE, TESSERA_DATASTORE_UPDATE, TESSERA_DATASTORE_DELETE,
                    TESSERA_DATASTORE_TEST_CONNECTION,

                    TESSERA_LAYER_LIST, TESSERA_LAYER_VIEW,
                    TESSERA_LAYER_CREATE, TESSERA_LAYER_UPDATE, TESSERA_LAYER_DELETE,
                    TESSERA_LAYER_INTROSPECT,

                    TESSERA_SYNC_VIEW, TESSERA_SYNC_RUN, TESSERA_SYNC_CONFIGURE
            ),

            TesseraRole.TESSERA_VIEWER, EnumSet.of(
                    TESSERA_WORKSPACE_LIST, TESSERA_WORKSPACE_VIEW,
                    TESSERA_DATASTORE_LIST, TESSERA_DATASTORE_VIEW,
                    TESSERA_LAYER_LIST, TESSERA_LAYER_VIEW,
                    TESSERA_SYNC_VIEW
            ),

            TesseraRole.TESSERA_SYNC_OPERATOR, EnumSet.of(
                    TESSERA_WORKSPACE_LIST, TESSERA_WORKSPACE_VIEW,
                    TESSERA_LAYER_LIST, TESSERA_LAYER_VIEW,
                    TESSERA_SYNC_VIEW, TESSERA_SYNC_RUN, TESSERA_SYNC_CONFIGURE
            )
    );
}
