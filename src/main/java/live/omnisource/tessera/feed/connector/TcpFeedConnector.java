package live.omnisource.tessera.feed.connector;

import live.omnisource.tessera.feed.dto.FeedConfig;
import live.omnisource.tessera.feed.dto.FeedStatus;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public class TcpFeedConnector implements FeedConnector {

    private volatile FeedStatus.State currentState = FeedStatus.State.STOPPED;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread readerThread;
    private Socket socket;

    @Override
    public FeedConfig.FeedType type() { return FeedConfig.FeedType.TCP; }

    @Override
    public void start(FeedConfig config, Consumer<String> messageCallback) {
        running.set(true);
        currentState = FeedStatus.State.STARTING;
        var conn = config.connection();

        readerThread = Thread.ofVirtual().name("feed-tcp-" + config.name()).start(() -> {
            while (running.get()) {
                try {
                    socket = conn.isTls()
                            ? SSLSocketFactory.getDefault().createSocket(conn.host(), conn.resolvedPort())
                            : new Socket(conn.host(), conn.resolvedPort());

                    currentState = FeedStatus.State.RUNNING;
                    log.info("TCP feed connected: {} → {}:{}", config.name(), conn.host(), conn.resolvedPort());

                    try (var reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                        String line;
                        while (running.get() && (line = reader.readLine()) != null) {
                            if (!line.isBlank()) {
                                messageCallback.accept(line);
                            }
                        }
                    }
                } catch (Exception e) {
                    if (running.get()) {
                        log.warn("TCP feed {} disconnected: {}, reconnecting in 5s", config.name(), e.getMessage());
                        currentState = FeedStatus.State.RECONNECTING;
                        try { Thread.sleep(5000); } catch (InterruptedException ignored) { break; }
                    }
                }
            }
            currentState = FeedStatus.State.STOPPED;
        });
    }

    @Override
    public void stop() {
        running.set(false);
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        if (readerThread != null) readerThread.interrupt();
        currentState = FeedStatus.State.STOPPED;
    }

    @Override
    public FeedStatus.State state() { return currentState; }

    @Override
    public String testConnection(FeedConfig config) throws Exception {
        var conn = config.connection();
        try (var s = conn.isTls()
                ? SSLSocketFactory.getDefault().createSocket(conn.host(), conn.resolvedPort())
                : new Socket(conn.host(), conn.resolvedPort())) {
            return "Connected to " + conn.host() + ":" + conn.resolvedPort();
        }
    }
}