package live.omnisource.tessera.stream.adapter.sse;

import live.omnisource.tessera.stream.StreamBroker;
import live.omnisource.tessera.stream.StreamSubscription;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Envelope;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/stream/sse")
public class SseStreamController {
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    private final StreamBroker streamBroker;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseStreamController(StreamBroker streamBroker, ObjectMapper objectMapper) {
        this.streamBroker = streamBroker;
        this.objectMapper = objectMapper;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @RequestParam(required = false) UUID sourceId,
            @RequestParam(required = false) String sourceTable,
            @RequestParam(required = false) Double minX,
            @RequestParam(required = false) Double minY,
            @RequestParam(required = false) Double maxX,
            @RequestParam(required = false) Double maxY,
            @RequestParam(required = false) String since
    ) {
        String subscriptionId = UUID.randomUUID().toString();
        var emitter = new SseEmitter(SSE_TIMEOUT_MS);

        Envelope bbox = null;
        if (minX != null && minY != null && maxX != null && maxY != null) {
            bbox = new Envelope(minX, maxX, minY, maxY);
        }

        Instant cursor = since != null ? Instant.parse(since) : Instant.now();

        var subscription = new StreamSubscription(
                subscriptionId,
                StreamSubscription.Protocol.SSE,
                sourceId,
                sourceTable,
                bbox,
                cursor);

        streamBroker.subscribe(subscription, features -> deliverSse(subscriptionId, emitter, features));
        emitters.put(subscriptionId, emitter);

        emitter.onCompletion(() -> cleanup(subscriptionId));
        emitter.onTimeout(() -> cleanup(subscriptionId));
        emitter.onError(e -> cleanup(subscriptionId));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(objectMapper.writeValueAsString(Map.of(
                            "subscriptionId", subscriptionId,
                            "protocol", "SSE",
                            "sourceId", sourceId != null ? sourceId.toString() : "all",
                            "spatialFilter", bbox != null ? bbox.toString() : "global",
                            "cursor", cursor.toString()
                    ))));
        } catch (IOException e) {
            log.warn("Failed to send SSE connect event: {}", e.getMessage());
            cleanup(subscriptionId);
        }

        log.info("SSE stream opened: id={} sourceId={} bbox={}",
                subscriptionId, sourceId, bbox != null ? bbox : "global");
        return emitter;
    }

    private void deliverSse(String subId, SseEmitter emitter, List<Map<String, Object>> features) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "type", "features",
                    "count", features.size(),
                    "timestamp", Instant.now().toString(),
                    "features", features
            ));

            emitter.send(SseEmitter.event()
                    .name("features")
                    .id(UUID.randomUUID().toString())
                    .data(json, MediaType.APPLICATION_JSON));

        } catch (IOException e) {
            log.debug("SSE delivery failed for {} — client likely disconnected", subId);
            cleanup(subId);
        }
    }

    private void cleanup(String subId) {
        emitters.remove(subId);
        streamBroker.unsubscribe(subId);
    }
}
