package live.omnisource.tessera.stream.event;

import java.time.Instant;
import java.util.UUID;

import org.locationtech.jts.geom.Envelope;

public record FeatureIngestEvent(
    UUID sourceId,
    String sourceTable,
    int featureCount,
    Envelope envelope,
    Instant minUpdatedAt,
    Instant maxUpdatedAt,
    Instant publishedAt) {
  public FeatureIngestEvent(
      UUID sourceId,
      String sourceTable,
      int featureCount,
      Envelope envelope,
      Instant minUpdatedAt,
      Instant maxUpdatedAt) {
    this(sourceId, sourceTable, featureCount, envelope, minUpdatedAt, maxUpdatedAt, Instant.now());
  }
}
