package live.omnisource.tessera.stream.api;

import live.omnisource.tessera.security.rbac.TesseraRole;
import live.omnisource.tessera.security.rbac.annotations.RequireGlobalRole;
import live.omnisource.tessera.stream.StreamBroker;
import live.omnisource.tessera.stream.StreamSubscription;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequireGlobalRole(TesseraRole.TESSERA_OPS_VIEWER)
@RequestMapping("/api/streams")
public class StreamsApiController {

    private final StreamBroker broker;

    public StreamsApiController(StreamBroker broker) {
        this.broker = broker;
    }

    @GetMapping
    public Map<String, Object> status() {
        var subs = broker.activeSubscriptions();
        long sseCount = subs.stream()
                .filter(s -> s.protocol() == StreamSubscription.Protocol.SSE).count();
        long wsCount = subs.stream()
                .filter(s -> s.protocol() == StreamSubscription.Protocol.WEBSOCKET).count();
        long restCount = subs.stream()
                .filter(s -> s.protocol() == StreamSubscription.Protocol.REST_POLL).count();
        long totalDelivered = subs.stream().mapToLong(StreamSubscription::deliveredCount).sum();

        return Map.of(
                "total", subs.size(),
                "sse", sseCount,
                "websocket", wsCount,
                "restPoll", restCount,
                "totalDelivered", totalDelivered
        );
    }

    @GetMapping("/subscriptions")
    public List<Map<String, Object>> subscriptions() {
        return broker.activeSubscriptions().stream()
                .map(sub -> Map.<String, Object>of(
                        "id", sub.id(),
                        "protocol", sub.protocol().name(),
                        "sourceId", sub.sourceId() != null ? sub.sourceId().toString() : "all",
                        "spatialFilter", sub.spatialFilter() != null ? sub.spatialFilter().toString() : "global",
                        "cursor", sub.cursor().toString(),
                        "deliveredCount", sub.deliveredCount(),
                        "createdAt", sub.createdAt().toString(),
                        "active", sub.isActive()
                ))
                .toList();
    }
}