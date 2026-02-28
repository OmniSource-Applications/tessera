package live.omnisource.tessera.stream.event;

import org.locationtech.jts.geom.Envelope;

import java.time.Instant;
import java.util.UUID;

public record FeatureIngestEvent(
        UUID sourceId,
        String sourceTable,
        int featureCount,
        Envelope envelope,
        Instant minUpdatedAt,
        Instant maxUpdatedAt,
        Instant publishedAt
) {
    public FeatureIngestEvent(
            UUID sourceId,
            String sourceTable,
            int featureCount,
            Envelope envelope,
            Instant minUpdatedAt,
            Instant maxUpdatedAt) {
        this(
            sourceId,
            sourceTable,
            featureCount,
            envelope,
            minUpdatedAt,
            maxUpdatedAt,
            Instant.now()
        );
    }
}
