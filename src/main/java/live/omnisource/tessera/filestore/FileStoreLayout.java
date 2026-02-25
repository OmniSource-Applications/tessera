package live.omnisource.tessera.filestore;

import org.springframework.stereotype.Component;

/**
 * Defines the layout of the file store used by Tessera.
 */
@Component
public final class FileStoreLayout {
    public static final String ROOT = "./data_dir";

    // --- etc directories ---
    public static final String SERVICES = "etc/services";
    public static final String NETWORK = "etc/network";
    public static final String DATABASE = "etc/database";
    public static final String AUTH = "etc/auth";
    public static final String SECRETS = "etc/auth/secrets";
    public static final String RBAC = "etc/auth/rbac";
    public static final String SECURITY = "etc/security";
    public static final String CATALOG = "etc/catalog";
    public static final String WORKSPACES = "etc/catalog/workspaces";
    public static final String DATA_STORES = "etc/datastores";
    public static final String LAYER_GROUPS = "etc/catalog/layergroups";

    // --- var directories ---
    public static final String LOG = "var/log";
    public static final String TEMP = "var/temp";
    public static final String RUN = "var/run";
    public static final String SHARE_DEFAULTS = "var/share/defaults";
    public static final String SHARE_SCHEMAS = "var/share/schemas";

    public static final String CACHE = "cache";
    private FileStoreLayout() {}
}
