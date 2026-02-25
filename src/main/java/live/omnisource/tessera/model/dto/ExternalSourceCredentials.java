package live.omnisource.tessera.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalSourceCredentials(
        String type,            // POSTGIS | MYSQL | ORACLE | CASSANDRA | ELASTICSEARCH
        String url,             // JDBC URL for SQL sources
        String username,
        String password,
        String driverClassName, // optional override
        int poolSize,           // 0 = use default (8)
        // Cassandra-specific
        String contactPoints,
        int port,
        String datacenter,
        String keyspace,
        // Elasticsearch-specific
        String hosts,
        boolean verifySsl
) {
    // Convenience factory for JDBC sources
    public static ExternalSourceCredentials jdbc(String type, String url,
                                                 String username, String password) {
        return new ExternalSourceCredentials(type, url, username, password,
                null, 8, null, 0, null, null, null, true);
    }
}
