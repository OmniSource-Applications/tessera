package live.omnisource.tessera.stream.adapter.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import live.omnisource.tessera.stream.StreamBroker;
import live.omnisource.tessera.stream.StreamSubscription;
import live.omnisource.tessera.stream.StreamSubscription.Protocol;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Envelope;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for bidirectional feature streaming.
 *
 * <p>Clients connect to {@code /ws/stream} and send JSON commands to
 * control their subscription. The server pushes matching features
 * as they're ingested.</p>
 *
 * <h3>Client → Server messages:</h3>
 * <pre>{@code
 * // Subscribe (or update viewport)
 * {
 *   "action": "subscribe",
 *   "sourceId": "uuid-or-null",
 *   "sourceTable": "public.poi",
 *   "bbox": { "minX": -74.1, "minY": 40.6, "maxX": -73.8, "maxY": 40.9 },
 *   "since": "2026-02-28T10:00:00Z"
 * }
 *
 * // Update viewport (re-creates subscription with new bbox)
 * {
 *   "action": "viewport",
 *   "bbox": { "minX": -74.2, "minY": 40.5, "maxX": -73.7, "maxY": 41.0 }
 * }
 *
 * // Unsubscribe
 * { "action": "unsubscribe" }
 *
 * // Ping (keepalive)
 * { "action": "ping" }
 * }</pre>
 *
 * <h3>Server → Client messages:</h3>
 * <pre>{@code
 * // Features
 * { "type": "features", "count": 5, "features": [...] }
 *
 * // Ack
 * { "type": "ack", "action": "subscribe", "subscriptionId": "..." }
 *
 * // Pong
 * { "type": "pong" }
 * }</pre>
 */
@Slf4j
@Component
public class WebSocketStreamHandler extends TextWebSocketHandler {

    private final StreamBroker broker;
    private final ObjectMapper objectMapper;

    /** Maps WebSocket session ID → current subscription ID */
    private final ConcurrentHashMap<String, SessionState> sessions = new ConcurrentHashMap<>();

    public WebSocketStreamHandler(StreamBroker broker, ObjectMapper objectMapper) {
        this.broker = broker;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), new SessionState(session));
        log.info("WebSocket connected: sessionId={} remote={}", session.getId(),
                session.getRemoteAddress());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode msg = objectMapper.readTree(message.getPayload());
            String action = msg.has("action") ? msg.get("action").asText() : "";

            switch (action) {
                case "subscribe" -> handleSubscribe(session, msg);
                case "viewport"  -> handleViewport(session, msg);
                case "unsubscribe" -> handleUnsubscribe(session);
                case "ping" -> sendJson(session, Map.of("type", "pong"));
                default -> sendJson(session, Map.of("type", "error",
                        "message", "Unknown action: " + action));
            }
        } catch (Exception e) {
            log.warn("Error handling WS message from {}: {}",
                    session.getId(), e.getMessage());
            sendJson(session, Map.of("type", "error", "message", e.getMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        var state = sessions.remove(session.getId());
        if (state != null && state.subscriptionId != null) {
            broker.unsubscribe(state.subscriptionId);
        }
        log.info("WebSocket disconnected: sessionId={} status={}",
                session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        log.debug("WS transport error for {}: {}", session.getId(), ex.getMessage());
        var state = sessions.remove(session.getId());
        if (state != null && state.subscriptionId != null) {
            broker.unsubscribe(state.subscriptionId);
        }
    }

    // ── Command handlers ─────────────────────────────────────

    private void handleSubscribe(WebSocketSession session, JsonNode msg) {
        var state = sessions.get(session.getId());
        if (state == null) return;

        // Remove previous subscription if exists
        if (state.subscriptionId != null) {
            broker.unsubscribe(state.subscriptionId);
        }

        String subId = UUID.randomUUID().toString();
        UUID sourceId = msg.has("sourceId") && !msg.get("sourceId").isNull()
                ? UUID.fromString(msg.get("sourceId").asText()) : null;
        String sourceTable = msg.has("sourceTable") && !msg.get("sourceTable").isNull()
                ? msg.get("sourceTable").asText() : null;
        Envelope bbox = parseBbox(msg);
        Instant since = msg.has("since") && !msg.get("since").isNull()
                ? Instant.parse(msg.get("since").asText()) : Instant.now();

        var sub = new StreamSubscription(subId, Protocol.WEBSOCKET,
                sourceId, sourceTable, bbox, since);

        broker.subscribe(sub, features -> deliverWs(session, features));
        state.subscriptionId = subId;
        state.sourceId = sourceId;
        state.sourceTable = sourceTable;

        sendJson(session, Map.of(
                "type", "ack",
                "action", "subscribe",
                "subscriptionId", subId,
                "sourceId", sourceId != null ? sourceId.toString() : "all",
                "spatialFilter", bbox != null ? bboxToMap(bbox) : "global",
                "cursor", since.toString()
        ));
    }

    private void handleViewport(WebSocketSession session, JsonNode msg) {
        var state = sessions.get(session.getId());
        if (state == null) return;

        // Remove current subscription and re-create with new bbox
        if (state.subscriptionId != null) {
            broker.unsubscribe(state.subscriptionId);
        }

        String subId = UUID.randomUUID().toString();
        Envelope bbox = parseBbox(msg);
        Instant cursor = Instant.now(); // Viewport change = fresh cursor

        var sub = new StreamSubscription(subId, Protocol.WEBSOCKET,
                state.sourceId, state.sourceTable, bbox, cursor);

        broker.subscribe(sub, features -> deliverWs(session, features));
        state.subscriptionId = subId;

        sendJson(session, Map.of(
                "type", "ack",
                "action", "viewport",
                "subscriptionId", subId,
                "spatialFilter", bbox != null ? bboxToMap(bbox) : "global"
        ));
    }

    private void handleUnsubscribe(WebSocketSession session) {
        var state = sessions.get(session.getId());
        if (state != null && state.subscriptionId != null) {
            broker.unsubscribe(state.subscriptionId);
            state.subscriptionId = null;
            sendJson(session, Map.of("type", "ack", "action", "unsubscribe"));
        }
    }

    // ── Delivery ─────────────────────────────────────────────

    private void deliverWs(WebSocketSession session, List<Map<String, Object>> features) {
        if (!session.isOpen()) return;
        sendJson(session, Map.of(
                "type", "features",
                "count", features.size(),
                "timestamp", Instant.now().toString(),
                "features", features
        ));
    }

    // ── Helpers ──────────────────────────────────────────────

    private void sendJson(WebSocketSession session, Object payload) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        } catch (Exception e) {
            log.debug("Failed to send WS message to {}: {}", session.getId(), e.getMessage());
        }
    }

    private Envelope parseBbox(JsonNode msg) {
        if (!msg.has("bbox") || msg.get("bbox").isNull()) return null;
        JsonNode bbox = msg.get("bbox");
        return new Envelope(
                bbox.get("minX").asDouble(),
                bbox.get("maxX").asDouble(),
                bbox.get("minY").asDouble(),
                bbox.get("maxY").asDouble()
        );
    }

    private Map<String, Double> bboxToMap(Envelope env) {
        return Map.of("minX", env.getMinX(), "minY", env.getMinY(),
                "maxX", env.getMaxX(), "maxY", env.getMaxY());
    }

    /** Mutable session state — one per WebSocket connection. */
    private static class SessionState {
        final WebSocketSession session;
        volatile String subscriptionId;
        volatile UUID sourceId;
        volatile String sourceTable;

        SessionState(WebSocketSession session) {
            this.session = session;
        }
    }
}