package live.omnisource.tessera.feed.connector;

import live.omnisource.tessera.feed.dto.FeedConfig;
import live.omnisource.tessera.feed.dto.FeedStatus;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class RestPollFeedConnector implements FeedConnector {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1,
            Thread.ofVirtual().name("feed-rest-", 0).factory());

    private volatile FeedStatus.State currentState = FeedStatus.State.STOPPED;
    private ScheduledFuture<?> pollFuture;

    @Override
    public FeedConfig.FeedType type() { return FeedConfig.FeedType.REST_POLL; }

    @Override
    public void start(FeedConfig config, Consumer<String> messageCallback) {
        var conn = config.connection();
        currentState = FeedStatus.State.STARTING;

        var reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(conn.url()))
                .timeout(Duration.ofSeconds(30));

        if (conn.headers() != null) {
            conn.headers().forEach(reqBuilder::header);
        }

        HttpRequest request = "POST".equalsIgnoreCase(conn.resolvedMethod())
                ? reqBuilder.POST(HttpRequest.BodyPublishers.ofString(conn.body() != null ? conn.body() : "")).build()
                : reqBuilder.GET().build();

        pollFuture = scheduler.scheduleWithFixedDelay(() -> {
            try {
                HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    currentState = FeedStatus.State.RUNNING;
                    messageCallback.accept(resp.body());
                } else {
                    log.warn("Feed {} poll returned HTTP {}", config.name(), resp.statusCode());
                    currentState = FeedStatus.State.ERROR;
                }
            } catch (Exception e) {
                log.warn("Feed {} poll failed: {}", config.name(), e.getMessage());
                currentState = FeedStatus.State.ERROR;
            }
        }, 0, conn.resolvedPollIntervalMs(), TimeUnit.MILLISECONDS);

        currentState = FeedStatus.State.RUNNING;
        log.info("REST poll feed started: {} → {} every {}ms", config.name(), conn.url(), conn.resolvedPollIntervalMs());
    }

    @Override
    public void stop() {
        if (pollFuture != null) pollFuture.cancel(true);
        currentState = FeedStatus.State.STOPPED;
    }

    @Override
    public FeedStatus.State state() { return currentState; }

    @Override
    public String testConnection(FeedConfig config) throws Exception {
        var conn = config.connection();
        var req = HttpRequest.newBuilder()
                .uri(URI.create(conn.url()))
                .timeout(Duration.ofSeconds(10))
                .GET().build();
        var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            return "HTTP " + resp.statusCode() + " (" + resp.body().length() + " bytes)";
        }
        throw new RuntimeException("HTTP " + resp.statusCode());
    }
}