package live.omnisource.tessera.feed.connector;

import live.omnisource.tessera.feed.dto.FeedConfig;
import live.omnisource.tessera.feed.dto.FeedStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public class KafkaFeedConnector implements FeedConnector {

    private volatile FeedStatus.State currentState = FeedStatus.State.STOPPED;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread consumerThread;
    private KafkaConsumer<String, String> consumer;

    @Override
    public FeedConfig.FeedType type() { return FeedConfig.FeedType.KAFKA; }

    @Override
    public void start(FeedConfig config, Consumer<String> messageCallback) {
        running.set(true);
        currentState = FeedStatus.State.STARTING;
        var conn = config.connection();

        consumerThread = Thread.ofVirtual().name("feed-kafka-" + config.name()).start(() -> {
            try {
                consumer = createConsumer(conn);
                consumer.subscribe(List.of(conn.topic()));
                currentState = FeedStatus.State.RUNNING;
                log.info("Kafka feed started: {} → topic={} servers={}",
                        config.name(), conn.topic(), conn.bootstrapServers());

                while (running.get()) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                    records.forEach(record -> messageCallback.accept(record.value()));
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.error("Kafka feed {} failed: {}", config.name(), e.getMessage());
                    currentState = FeedStatus.State.ERROR;
                }
            } finally {
                if (consumer != null) {
                    try { consumer.close(Duration.ofSeconds(5)); } catch (Exception ignored) {}
                }
                currentState = FeedStatus.State.STOPPED;
            }
        });
    }

    @Override
    public void stop() {
        running.set(false);
        if (consumer != null) consumer.wakeup();
        if (consumerThread != null) consumerThread.interrupt();
        currentState = FeedStatus.State.STOPPED;
    }

    @Override
    public FeedStatus.State state() { return currentState; }

    @Override
    public String testConnection(FeedConfig config) throws Exception {
        var conn = config.connection();
        try (var testConsumer = createConsumer(conn)) {
            testConsumer.subscribe(List.of(conn.topic()));
            testConsumer.poll(Duration.ofSeconds(5));
            var partitions = testConsumer.partitionsFor(conn.topic());
            return "Connected to " + conn.bootstrapServers() + ", topic '" + conn.topic()
                    + "' has " + partitions.size() + " partition(s)";
        }
    }

    private KafkaConsumer<String, String> createConsumer(FeedConfig.ConnectionConfig conn) {
        var props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, conn.bootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG,
                conn.groupId() != null ? conn.groupId() : "tessera-feed-" + System.nanoTime());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                conn.keyDeserializer() != null ? conn.keyDeserializer() : StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                conn.valueDeserializer() != null ? conn.valueDeserializer() : StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        // Additional properties
        if (conn.kafkaProperties() != null) {
            props.putAll(conn.kafkaProperties());
        }

        return new KafkaConsumer<>(props);
    }
}