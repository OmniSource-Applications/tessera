package live.omnisource.tessera.datasource.connector.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import live.omnisource.tessera.config.ApplicationProperties;
import live.omnisource.tessera.filestore.crypto.SecureFileStore;
import live.omnisource.tessera.model.dto.ExternalSourceCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class CassandraSessionFactory {
    private static final int DEFAULT_PORT = 9042;
    private static final String DEFAULT_DATACENTER = "datacenter1";

    private final ApplicationProperties applicationProperties;
    private final SecureFileStore secureFileStore;
    private final ObjectMapper objectMapper;
    private final LoadingCache<String, CqlSession> sessions;

    public CassandraSessionFactory(
            ApplicationProperties applicationProperties,
            SecureFileStore secureFileStore,
            ObjectMapper objectMapper) {
        this.applicationProperties = applicationProperties;
        this.secureFileStore = secureFileStore;
        this.objectMapper = objectMapper;
        this.sessions = CacheBuilder.newBuilder()
                .maximumSize(applicationProperties.connectors().cassandra().sessionsMax())
                .expireAfterAccess(applicationProperties.connectors().cassandra().expirationMinutes(), TimeUnit.MINUTES)
                .removalListener((RemovalListener<String, CqlSession>) notification -> {
                    var session = notification.getValue();
                    if (session != null && !session.isClosed()) {
                        session.close();
                        log.info("Evicted Cassandra session for: {}", notification.getKey());
                    }
                })
                .build(CacheLoader.from(this::build));
    }

    public CqlSession sessionFor(String secretRefKey) {
        return sessions.getUnchecked(secretRefKey);
    }

    public CqlSession sessionFor(ExternalSourceCredentials credentials) {
        return CqlSession.builder()
                .addContactPoint(new InetSocketAddress(credentials.hosts(), credentials.port()))
                .withLocalDatacenter(credentials.datacenter())
                .withAuthCredentials(credentials.username(), credentials.password())
                .withKeyspace(credentials.keyspace())
                .build();
    }

    public void evict(String secretRefKey) {
        sessions.invalidate(secretRefKey);
    }

    private CqlSession build(String secretRefKey) {
        try {
            var credentials = readCredentials(secretRefKey);
            CqlSessionBuilder builder = CqlSession.builder()
                    .withLocalDatacenter(credentials.datacenter() != null ? credentials.datacenter() : DEFAULT_DATACENTER)
                    .withAuthCredentials(credentials.username(), credentials.password())
                    .withKeyspace(credentials.keyspace());

            int port = credentials.port() > 0 ? credentials.port() : DEFAULT_PORT;
            Arrays.stream(credentials.contactPoints().split(","))
                    .map(String::trim)
                    .map(host -> new InetSocketAddress(host, port))
                    .forEach(builder::addContactPoint);

            log.info("Built Cassandra session for: {} keyspace={}", secretRefKey, credentials.keyspace());
            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Cassandra session for: " + secretRefKey, e);
        }
    }

    private ExternalSourceCredentials readCredentials(String secretRefKey) throws IOException {
        byte[] json = secureFileStore.get(secretRefKey);
        return objectMapper.readValue(json, ExternalSourceCredentials.class);
    }


}
