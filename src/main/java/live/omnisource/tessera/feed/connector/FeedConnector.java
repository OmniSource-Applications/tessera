package live.omnisource.tessera.feed.connector;

import live.omnisource.tessera.feed.dto.FeedConfig;
import live.omnisource.tessera.feed.dto.FeedStatus;

import java.util.function.Consumer;

/**
 * Interface for live feed protocol connectors.
 *
 * <p>Each connector handles one protocol type (REST polling, TCP, WebSocket,
 * Kafka, AMQP). The connector manages its own connection lifecycle and delivers
 * raw messages to the provided callback.</p>
 *
 * <p>Connectors must be thread-safe — a single instance handles one feed, but
 * multiple feeds of the same type each get their own connector instance.</p>
 */
public interface FeedConnector {

    /**
     * The feed type this connector handles.
     */
    FeedConfig.FeedType type();

    /**
     * Start receiving messages. The callback is invoked for each raw message
     * (typically a JSON string or byte array). Implementations should handle
     * reconnection internally.
     *
     * @param config          feed configuration
     * @param messageCallback called for each received message (raw String)
     */
    void start(FeedConfig config, Consumer<String> messageCallback);

    /**
     * Stop the connector and release all resources.
     */
    void stop();

    /**
     * Current status of this connector instance.
     */
    FeedStatus.State state();

    /**
     * Test connectivity without starting ingestion.
     * Returns a description on success, throws on failure.
     */
    String testConnection(FeedConfig config) throws Exception;
}