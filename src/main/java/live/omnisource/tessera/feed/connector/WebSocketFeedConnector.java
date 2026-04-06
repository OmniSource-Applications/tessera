package live.omnisource.tessera.feed.connector;

import live.omnisource.tessera.feed.dto.FeedConfig;
import live.omnisource.tessera.feed.dto.FeedStatus;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public class WebSocketFeedConnector implements FeedConnector {

    private volatile FeedStatus.State currentState = FeedStatus.State.STOPPED;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private WebSocket webSocket;
    private Thread reconnectThread;

    @Override
    public FeedConfig.FeedType type() { return FeedConfig.FeedType.WEBSOCKET; }

    @Override
    public void start(FeedConfig config, Consumer<String> messageCallback) {
        running.set(true);
        currentState = FeedStatus.State.STARTING;

        reconnectThread = Thread.ofVirtual().name("feed-ws-" + config.name()).start(() -> {
            while (running.get()) {
                try {
                    connect(config, messageCallback);
                    // Block until disconnected
                    while (running.get() && currentState == FeedStatus.State.RUNNING) {
                        Thread.sleep(1000);
                    }
                } catch (Exception e) {
                    if (running.get()) {
                        log.warn("WS feed {} error: {}, reconnecting in 5s", config.name(), e.getMessage());
                        currentState = FeedStatus.State.RECONNECTING;
                        try { Thread.sleep(5000); } catch (InterruptedException ignored) { break; }
                    }
                }
            }
        });
    }

    private void connect(FeedConfig config, Consumer<String> messageCallback) throws Exception {
        var conn = config.connection();
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {

            webSocket = client.newWebSocketBuilder()
                    .buildAsync(URI.create(conn.wsUrl()), new WebSocket.Listener() {
                        private final StringBuilder buffer = new StringBuilder();

                        @Override
                        public void onOpen(WebSocket ws) {
                            currentState = FeedStatus.State.RUNNING;
                            log.info("WS feed connected: {} → {}", config.name(), conn.wsUrl());

                            // Send subscribe message if configured
                            if (conn.wsSubscribeMessage() != null && !conn.wsSubscribeMessage().isBlank()) {
                                ws.sendText(conn.wsSubscribeMessage(), true);
                            }
                            ws.request(1);
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                            buffer.append(data);
                            if (last) {
                                String msg = buffer.toString();
                                if (log.isDebugEnabled()) {
                                    log.debug("WS feed {} received message ({} chars)", config.name(), msg.length());
                                }
                                messageCallback.accept(msg);
                                buffer.setLength(0);
                            }
                            ws.request(1);
                            return null;
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
                            log.info("WS feed {} closed: {} {}", config.name(), code, reason);
                            currentState = FeedStatus.State.RECONNECTING;
                            return null;
                        }

                        @Override
                        public void onError(WebSocket ws, Throwable error) {
                            log.warn("WS feed {} error: {}", config.name(), error.getMessage());
                            currentState = FeedStatus.State.ERROR;
                        }
                    })
                    .join();
        }
    }

    @Override
    public void stop() {
        running.set(false);
        if (webSocket != null) {
            try { webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown").join(); }
            catch (Exception ignored) {}
        }
        if (reconnectThread != null) reconnectThread.interrupt();
        currentState = FeedStatus.State.STOPPED;
    }

    @Override
    public FeedStatus.State state() { return currentState; }

    @Override
    public String testConnection(FeedConfig config) throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        var ws = client.newWebSocketBuilder()
                .buildAsync(URI.create(config.connection().wsUrl()), new WebSocket.Listener() {})
                .join();
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "test").join();
        return "WebSocket handshake OK: " + config.connection().wsUrl();
    }
}