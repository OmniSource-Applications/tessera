package live.omnisource.tessera.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;


@ConfigurationProperties(prefix = "tessera")
public record ApplicationProperties(
        H3Properties h3,
        SyncProperties sync,
        CatalogProperties catalog,
        CryptoProperties crypto,
        ConnectorsProperties connectors
) {
    public record H3Properties(
            @DefaultValue({"3", "5", "7", "9", "11"}) List<Integer> resolutions,
            @DefaultValue("10000") int batchSize,
            @DefaultValue("true") boolean compactionEnabled
    ) {}

    public record SyncProperties(
            @DefaultValue("5000") long pollingIntervalMs,
            @DefaultValue("5000") int batchSize,
            @DefaultValue("true") boolean cdcEnabled,
            @DefaultValue("10000") long checkpointIntervalMs
    ) {}

    public record CatalogProperties(
            @DefaultValue("300") int cacheTtlSeconds
    ) {}

    public record CryptoProperties(String salt) {}

    public record ConnectorsProperties(
            CassandraProperties cassandra
    ) {
        public record CassandraProperties(
                @DefaultValue("10") int sessionsMax,
                @DefaultValue("30") int expirationMinutes
        ) {}

    }
}
