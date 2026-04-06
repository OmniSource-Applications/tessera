package live.omnisource.tessera.apikey;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import lombok.extern.slf4j.Slf4j;

/**
 * Token-bucket rate limiter for API keys.
 *
 * <p>Each key gets a bucket that refills at {@code rateLimitRpm / 60} tokens per second, up to a
 * burst capacity equal to the RPM. Buckets are lazily created and expire after 5 minutes of
 * inactivity.
 */
@Slf4j
@Component
public class ApiKeyRateLimiter {

  private final LoadingCache<UUID, TokenBucket> buckets;

  /**
   * Constructs an {@code ApiKeyRateLimiter} instance.
   *
   * <p>Initializes the rate limiter with a cache of token buckets for tracking API key usage. The
   * cache has the following properties: - Maximum size of 10,000 buckets. - Buckets expire after 5
   * minutes of inactivity.
   *
   * <p>The token bucket for each API key is lazily created with an initial capacity of 60 tokens,
   * corresponding to a default rate limit of 60 requests per minute. This default capacity can be
   * overridden by calling the appropriate configuration or consumption methods.
   */
  public ApiKeyRateLimiter() {
    this.buckets =
        CacheBuilder.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .build(
                CacheLoader.from(id -> new TokenBucket(60))); // default, overridden by configure()
  }

  /**
   * Attempts to consume a token for the specified API key under the given rate limit constraints.
   * If a token is successfully consumed, the method returns {@code true}, indicating the operation
   * is permitted. Otherwise, it returns {@code false}, indicating the rate limit has been exceeded.
   *
   * @param keyId The unique identifier of the API key for which the token consumption is being
   *     attempted.
   * @param rateLimitRpm The maximum number of tokens allowed per minute (RPM) for the specified API
   *     key. This defines the rate limit for the token bucket associated with the key.
   * @return {@code true} if a token was successfully consumed from the bucket; {@code false} if the
   *     rate limit has been exceeded and no tokens are available for consumption.
   */
  public boolean tryConsume(UUID keyId, int rateLimitRpm) {
    final var bucket = buckets.getUnchecked(keyId);
    bucket.setCapacity(rateLimitRpm);
    return bucket.tryConsume();
  }

  /**
   * Retrieves the number of available tokens for the specified API key from its associated token
   * bucket. If no bucket is found for the provided key, returns -1.
   *
   * @param keyId The unique identifier of the API key for which the remaining tokens are to be
   *     retrieved.
   * @return The number of tokens currently available in the bucket associated with the API key, or
   *     -1 if no bucket exists for the specified key.
   */
  public long remaining(UUID keyId) {
    final var bucket = buckets.getIfPresent(keyId);
    return bucket != null ? bucket.available() : -1;
  }

  /**
   * A thread-safe implementation of a token bucket rate limiter. The token bucket algorithm
   * controls access to resources by limiting the number of operations within a defined time
   * interval. Tokens represent the right to perform an operation.
   *
   * <p>Each bucket has a fixed capacity and refills tokens at a steady rate, proportional to its
   * capacity, over time. If the bucket is full, no refills occur until tokens are consumed.
   *
   * <p>This class is designed to be used in rate limiting scenarios, such as controlling API
   * access.
   */
  static class TokenBucket {
    private volatile int capacity;
    private final AtomicLong tokens;
    private volatile long lastRefillNanos;

    TokenBucket(int capacity) {
      this.capacity = capacity;
      this.tokens = new AtomicLong(capacity);
      this.lastRefillNanos = System.nanoTime();
    }

    void setCapacity(int newCapacity) {
      this.capacity = newCapacity;
    }

    boolean tryConsume() {
      refill();
      long current = tokens.get();
      while (current > 0) {
        if (tokens.compareAndSet(current, current - 1)) {
          return true;
        }
        current = tokens.get();
      }
      return false;
    }

    long available() {
      refill();
      return tokens.get();
    }

    private void refill() {
      final long now = System.nanoTime();
      final long elapsed = now - lastRefillNanos;
      final long tokensToAdd = (elapsed * capacity) / 60_000_000_000L;
      if (tokensToAdd > 0) {
        lastRefillNanos = now;
        final long current = tokens.get();
        final long newValue = Math.min(capacity, current + tokensToAdd);
        tokens.set(newValue);
      }
    }
  }
}
