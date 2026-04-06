package live.omnisource.tessera.apikey.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents an API key entity that is stored in the database under the "tessera" schema in the
 * "api_keys" table. An API key is used for authentication and authorization purposes, with
 * additional fields to track its usage and lifecycle states. <br>
 * </br> This entity provides fields for storing relevant information such as the key name, key
 * prefix, key hash, owner, allowed scopes, rate limit, activity status, and timestamps for events
 * like creation, expiration, and revocation. The entity also includes transient methods for
 * determining the current usability of the key. <br>
 * </br> Key Attributes: - `id`: The unique identifier for the API key. - `name`: The human-readable
 * name of the API key. - `keyPrefix`: The prefix of the API key, typically used for quick lookups.
 * - `keyHash`: The hashed value of the API key, used for secure storage. - `owner`: The identifier
 * of the entity or user that owns the key. - `scopes`: The list of authorized scopes defining
 * access permissions for the key. - `rateLimitRpm`: The allowed rate limit for requests per minute.
 * - `active`: Whether the key is active and usable. - `lastUsedAt`: Timestamp of the last time the
 * key was used. - `requestCount`: The total number of requests made with this key. - `expiresAt`:
 * The timestamp after which the key becomes unusable due to expiration. - `createdAt`: The
 * timestamp when the key was created. - `revokedAt`: The timestamp when the key was revoked, if
 * applicable. <br>
 * </br> Lifecycle Hooks: - `@PrePersist`: Sets the `createdAt` timestamp to the current instant
 * when the entity is being persisted for the first time. <br>
 * </br> Transient Methods: - `isExpired()`: Determines if the API key is expired based on the
 * `expiresAt` timestamp. - `isRevoked()`: Checks if the API key has been revoked. - `isUsable()`:
 * Evaluates if the API key is usable. A key is usable if it is active, not expired, and not
 * revoked.
 */
@Getter
@Setter
@Entity
@Table(name = "api_keys", schema = "tessera")
public class ApiKey {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(name = "key_prefix", nullable = false)
  private String keyPrefix;

  @Column(name = "key_hash", nullable = false, unique = true)
  private String keyHash;

  @Column(nullable = false)
  private String owner;

  @Column(columnDefinition = "TEXT[]")
  @JdbcTypeCode(SqlTypes.ARRAY)
  private List<String> scopes;

  @Column(name = "rate_limit_rpm", nullable = false)
  private int rateLimitRpm = 60;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  @Column(name = "request_count", nullable = false)
  private long requestCount = 0;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @PrePersist
  void prePersist() {
    createdAt = Instant.now();
  }

  @Transient
  public boolean isExpired() {
    return expiresAt != null && Instant.now().isAfter(expiresAt);
  }

  @Transient
  public boolean isRevoked() {
    return revokedAt != null;
  }

  @Transient
  public boolean isUsable() {
    return active && !isExpired() && !isRevoked();
  }
}
