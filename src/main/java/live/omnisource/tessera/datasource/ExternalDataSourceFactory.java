package live.omnisource.tessera.datasource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import live.omnisource.tessera.model.dto.ExternalSourceCredentials;
import live.omnisource.tessera.filestore.crypto.SecretRef;
import live.omnisource.tessera.filestore.crypto.SecureFileStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ExternalDataSourceFactory {

    private static final int MAX_POOLS = 64;

    private final SecureFileStore secureFileStore;
    private final ObjectMapper mapper;

    // Bounded cache — evicts LRU and closes pools on removal
    private final LoadingCache<String, HikariDataSource> pool;

    public ExternalDataSourceFactory(SecureFileStore secureFileStore, ObjectMapper mapper) {
        this.secureFileStore = secureFileStore;
        this.mapper = mapper;
        this.pool = CacheBuilder.newBuilder()
                .maximumSize(MAX_POOLS)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .removalListener((RemovalListener<String, HikariDataSource>) notification -> {
                    var ds = notification.getValue();
                    if (ds != null && !ds.isClosed()) {
                        ds.close();
                        log.info("Evicted connection pool for: {} (reason: {})",
                                notification.getKey(), notification.getCause());
                    }
                })
                .build(CacheLoader.from(this::buildFromStore));
    }

    /**
     * Resolve a DataSource for an external source whose credentials are stored
     * under the given key.
     *
     * @param secretRefKey  e.g. "workspaces/demo/datastores/demo-pg"
     */
    public DataSource forSecretRef(String secretRefKey) {
        return pool.getUnchecked(secretRefKey);
    }

    /**
     * Store credentials for a new external source into the SecureFileStore,
     * returning the SecretRef that identifies it.
     */
    public SecretRef storeCredentials(String sourceKey, ExternalSourceCredentials creds) {
        try {
            byte[] json = mapper.writeValueAsBytes(creds);
            secureFileStore.put(sourceKey, json);
            log.info("Stored credentials for external source: {}", sourceKey);
            return SecretRef.of(sourceKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to store credentials for: " + sourceKey, e);
        }
    }

    /** Evict a cached pool (call when a source is updated or removed). */
    public void evict(String secretRefKey) {
        pool.invalidate(secretRefKey);
    }

    private HikariDataSource buildFromStore(String secretRefKey) {
        try {
            byte[] json = secureFileStore.get(secretRefKey);
            var creds = mapper.readValue(json, ExternalSourceCredentials.class);
            return buildPool(secretRefKey, creds);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build DataSource for: " + secretRefKey, e);
        }
    }

    private HikariDataSource buildPool(String name, ExternalSourceCredentials creds) {
        var cfg = new HikariConfig();
        cfg.setPoolName("ext-" + name.replace("/", "-"));
        cfg.setJdbcUrl(creds.url());
        cfg.setUsername(creds.username());
        cfg.setPassword(creds.password());
        cfg.setMaximumPoolSize(creds.poolSize() > 0 ? creds.poolSize() : 8);
        cfg.setMinimumIdle(1);
        cfg.setConnectionTimeout(5_000);
        cfg.setReadOnly(true);  // external sources are always read-only

        if (creds.driverClassName() != null) {
            cfg.setDriverClassName(creds.driverClassName());
        }

        log.info("Built connection pool for external source: {} ({})", name, creds.url());
        return new HikariDataSource(cfg);
    }
}