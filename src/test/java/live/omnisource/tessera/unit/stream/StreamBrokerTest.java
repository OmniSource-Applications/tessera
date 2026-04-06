package live.omnisource.tessera.unit.stream;

import live.omnisource.tessera.stream.StreamBroker;
import live.omnisource.tessera.stream.StreamSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StreamBrokerTest {

    StreamBroker broker;

    @BeforeEach
    void setUp() {
        broker = new StreamBroker(mock(NamedParameterJdbcTemplate.class));
    }

    @Test
    void subscribe_registersSubscription() {
        var sub = new StreamSubscription(
                "sub-1", StreamSubscription.Protocol.SSE, UUID.randomUUID(), null, null, Instant.now()
        );

        broker.subscribe(sub, features -> {});

        assertThat(broker.subscriberCount()).isEqualTo(1);
        assertThat(broker.activeSubscriptions()).hasSize(1);
    }

    @Test
    void unsubscribe_removesSubscription() {
        var sub = new StreamSubscription(
                "sub-2", StreamSubscription.Protocol.SSE, UUID.randomUUID(), null, null, Instant.now()
        );

        broker.subscribe(sub, features -> {});
        assertThat(broker.subscriberCount()).isEqualTo(1);

        broker.unsubscribe("sub-2");
        assertThat(broker.subscriberCount()).isEqualTo(0);
    }

    @Test
    void unsubscribe_noOpForUnknownId() {
        broker.unsubscribe("nonexistent");
        assertThat(broker.subscriberCount()).isEqualTo(0);
    }

    @Test
    void multipleSubscriptions_trackedIndependently() {
        for (int i = 0; i < 5; i++) {
            broker.subscribe(
                    new StreamSubscription("sub-" + i, StreamSubscription.Protocol.SSE, null, null, null, Instant.now()),
                    features -> {}
            );
        }

        assertThat(broker.subscriberCount()).isEqualTo(5);

        broker.unsubscribe("sub-2");
        assertThat(broker.subscriberCount()).isEqualTo(4);
    }
}