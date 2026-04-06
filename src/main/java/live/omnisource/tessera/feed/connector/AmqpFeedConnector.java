package live.omnisource.tessera.feed.connector;

import com.rabbitmq.client.*;
import live.omnisource.tessera.feed.dto.FeedConfig;
import live.omnisource.tessera.feed.dto.FeedStatus;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public class AmqpFeedConnector implements FeedConnector {

    private volatile FeedStatus.State currentState = FeedStatus.State.STOPPED;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Connection connection;
    private Channel channel;
    private Thread consumerThread;

    @Override
    public FeedConfig.FeedType type() { return FeedConfig.FeedType.AMQP; }

    @Override
    public void start(FeedConfig config, Consumer<String> messageCallback) {
        running.set(true);
        currentState = FeedStatus.State.STARTING;
        var conn = config.connection();

        consumerThread = Thread.ofVirtual().name("feed-amqp-" + config.name()).start(() -> {
            while (running.get()) {
                try {
                    var factory = new ConnectionFactory();
                    factory.setUri(conn.amqpUri());
                    factory.setAutomaticRecoveryEnabled(true);
                    factory.setNetworkRecoveryInterval(5000);

                    connection = factory.newConnection("tessera-feed-" + config.name());
                    channel = connection.createChannel();

                    // Declare queue if needed
                    String queueName = conn.queue();
                    if (queueName != null && !queueName.isBlank()) {
                        channel.queueDeclare(queueName, conn.isDurableQueue(), false, false, null);

                        // Bind to exchange if specified
                        if (conn.exchange() != null && !conn.exchange().isBlank()) {
                            String routingKey = conn.routingKey() != null ? conn.routingKey() : "#";
                            channel.queueBind(queueName, conn.exchange(), routingKey);
                        }
                    }

                    currentState = FeedStatus.State.RUNNING;
                    log.info("AMQP feed started: {} → queue={} exchange={}",
                            config.name(), queueName, conn.exchange());

                    channel.basicConsume(queueName, true, new DefaultConsumer(channel) {
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope,
                                                   AMQP.BasicProperties properties, byte[] body) {
                            messageCallback.accept(new String(body, StandardCharsets.UTF_8));
                        }
                    });

                    // Block until disconnected
                    while (running.get() && connection.isOpen()) {
                        Thread.sleep(1000);
                    }

                } catch (Exception e) {
                    if (running.get()) {
                        log.warn("AMQP feed {} error: {}, reconnecting in 5s", config.name(), e.getMessage());
                        currentState = FeedStatus.State.RECONNECTING;
                        cleanup();
                        try { Thread.sleep(5000); } catch (InterruptedException ignored) { break; }
                    }
                }
            }
            cleanup();
            currentState = FeedStatus.State.STOPPED;
        });
    }

    @Override
    public void stop() {
        running.set(false);
        cleanup();
        if (consumerThread != null) consumerThread.interrupt();
        currentState = FeedStatus.State.STOPPED;
    }

    @Override
    public FeedStatus.State state() { return currentState; }

    @Override
    public String testConnection(FeedConfig config) throws Exception {
        var factory = new ConnectionFactory();
        factory.setUri(config.connection().amqpUri());
        try (var conn = factory.newConnection("tessera-feed-test");
             var ch = conn.createChannel()) {
            return "AMQP connected: " + conn.getAddress().getHostAddress()
                    + ", server version " + conn.getServerProperties().get("version");
        }
    }

    private void cleanup() {
        try { if (channel != null && channel.isOpen()) channel.close(); } catch (Exception ignored) {}
        try { if (connection != null && connection.isOpen()) connection.close(); } catch (Exception ignored) {}
    }
}