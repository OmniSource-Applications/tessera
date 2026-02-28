package live.omnisource.tessera.stream;

import live.omnisource.tessera.stream.event.FeatureIngestEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
@Service
public class StreamBroker {

    private static final int DELIVERY_BATCH_LIMIT = 500;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    private final ConcurrentHashMap<String, ActiveSub> subscriptions = new ConcurrentHashMap<>();

    public StreamBroker(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void subscribe(StreamSubscription subscription, Consumer<List<Map<String, Object>>> callback) {
        subscriptions.put(subscription.id(), new ActiveSub(subscription, callback));
        log.info("Stream subscription registered: id={} protocol={} sourceId={} bbox={}",
                subscription.id(), subscription.protocol(), subscription.sourceId(),
                subscription.spatialFilter() != null ? subscription.spatialFilter() : "global");
    }

    public void unsubscribe(String subscriptionId) {
        var removed = subscriptions.remove(subscriptionId);
        if (removed != null) {
            removed.subscription.deactivate();
            log.info("Stream subscription removed: id={} delivered={}",
                    subscriptionId, removed.subscription.deliveredCount());
        }
    }

    public List<StreamSubscription> activeSubscriptions() {
        return subscriptions.values().stream()
                .map(ActiveSub::subscription)
                .filter(StreamSubscription::isActive)
                .toList();
    }

    public int subscriberCount() {
        return subscriptions.size();
    }

    @Async
    @EventListener
    public void onFeatureIngested(FeatureIngestEvent event) {
        if (subscriptions.isEmpty()) return;

        log.debug("Ingest event: source={} table={} count={} envelope={}",
                event.sourceId(), event.sourceTable(), event.featureCount(), event.envelope());

        for (var entry : subscriptions.entrySet()) {
            String subId = entry.getKey();
            ActiveSub active = entry.getValue();
            StreamSubscription subscription = active.subscription();

            if (!subscription.matches(event.sourceId(), event.sourceTable(), event.envelope())) {
                continue;
            }

            try {
                deleiverToSubscription(active, event);
            } catch (Exception e) {
                log.warn("Failed to deliver features to subscription {}: {}", subId, e.getMessage());
            }
        }
    }

    private void deleiverToSubscription(ActiveSub active, FeatureIngestEvent event) {
        StreamSubscription subscription = active.subscription;
        if (!subscription.isActive()) return;

        List<Map<String, Object>> features = queryFeaturesSince(subscription);
        if (features.isEmpty()) return;

        Instant maxUpdatedAt = features.stream()
                .map(f -> (Instant) f.get("updated_at"))
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(event.maxUpdatedAt());

        subscription.advanceCursor(maxUpdatedAt);
        subscription.incrementDelivered(features.size());

        log.debug("Delivered {} features to subscription {} (cursor now {})",
                features.size(), subscription.id(), maxUpdatedAt);
    }

    private List<Map<String, Object>> queryFeaturesSince(
            StreamSubscription sub) {

        var sql = new StringBuilder("""
                SELECT f.id, f.external_id, f.source_id, f.source_table,
                       ST_AsGeoJSON(f.geometry)::text AS geometry_json,
                       f.geometry_type, f.attributes, f.updated_at
                FROM tessera.geo_features f
                WHERE f.updated_at > :cursor
                """);

        var params = new HashMap<String, Object>();
        params.put("cursor", java.sql.Timestamp.from(sub.cursor()));

        // Source filter
        if (sub.sourceId() != null) {
            sql.append(" AND f.source_id = :sourceId::uuid");
            params.put("sourceId", sub.sourceId().toString());
        }
        if (sub.sourceTable() != null) {
            sql.append(" AND f.source_table = :sourceTable");
            params.put("sourceTable", sub.sourceTable());
        }

        // Spatial filter (bbox)
        if (sub.spatialFilter() != null) {
            sql.append(" AND ST_Intersects(f.geometry, ST_MakeEnvelope(:minX, :minY, :maxX, :maxY, 4326))");
            params.put("minX", sub.spatialFilter().getMinX());
            params.put("minY", sub.spatialFilter().getMinY());
            params.put("maxX", sub.spatialFilter().getMaxX());
            params.put("maxY", sub.spatialFilter().getMaxY());
        }

        sql.append(" ORDER BY f.updated_at ASC LIMIT :limit");
        params.put("limit", DELIVERY_BATCH_LIMIT);

        return jdbcTemplate.queryForList(sql.toString(), params);
    }

    private record ActiveSub(StreamSubscription subscription, Consumer<List<Map<String, Object>>> callback) {}
}
