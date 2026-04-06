package live.omnisource.tessera.unit.apikey;

import live.omnisource.tessera.apikey.ApiKeyRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyRateLimiterTest {

    ApiKeyRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new ApiKeyRateLimiter();
    }

    @Test
    void tryConsume_allowsFirstRequest() {
        UUID id = UUID.randomUUID();
        assertThat(limiter.tryConsume(id, 60)).isTrue();
    }

    @Test
    void tryConsume_exhaustsBucketAtLimit() {
        UUID id = UUID.randomUUID();
        int rpm = 5;

        // Consume all tokens
        for (int i = 0; i < rpm; i++) {
            assertThat(limiter.tryConsume(id, rpm)).isTrue();
        }

        // Next should be denied
        assertThat(limiter.tryConsume(id, rpm)).isFalse();
    }

    @Test
    void tryConsume_differentKeysAreIndependent() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        // Exhaust key1
        for (int i = 0; i < 3; i++) limiter.tryConsume(id1, 3);
        assertThat(limiter.tryConsume(id1, 3)).isFalse();

        // Key2 should still work
        assertThat(limiter.tryConsume(id2, 3)).isTrue();
    }

    @Test
    void remaining_returnsMinusOneForUnknownKey() {
        assertThat(limiter.remaining(UUID.randomUUID())).isEqualTo(-1);
    }

    @Test
    void remaining_decreasesAfterConsume() {
        UUID id = UUID.randomUUID();
        limiter.tryConsume(id, 10);
        long remaining = limiter.remaining(id);
        assertThat(remaining).isLessThan(10);
    }
}