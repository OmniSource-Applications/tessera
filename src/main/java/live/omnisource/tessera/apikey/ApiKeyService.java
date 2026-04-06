package live.omnisource.tessera.apikey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import live.omnisource.tessera.apikey.entity.ApiKey;
import live.omnisource.tessera.apikey.repository.ApiKeyRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages API key lifecycle: creation, validation, revocation.
 *
 * <p>Key format: {@code tsk_<32 hex chars>} (total 36 chars). Storage: SHA-256 hash of the full key
 * + 8-char prefix for fast lookup.
 */
@Slf4j
@Service
@Transactional
public class ApiKeyService {

  public static final String KEY_PREFIX = "tsk_";
  private static final int RAW_BYTES = 16;
  private static final int PREFIX_LEN = 8;

  public static final List<String> DEFAULT_SCOPES =
      List.of("QUERY_READ", "QUERY_EXECUTE", "SOURCES_READ", "STREAM_READ");

  public static final Set<String> ALL_SCOPES =
      Set.of(
          "QUERY_READ",
          "QUERY_EXECUTE",
          "SOURCES_READ",
          "SOURCES_WRITE",
          "STREAM_READ",
          "LAYERS_READ",
          "ADMIN");

  private final ApiKeyRepository repo;
  private final SecureRandom rng = new SecureRandom();

  /**
   * Constructs an instance of {@code ApiKeyService}.
   *
   * @param repo the {@link ApiKeyRepository} used for API key management operations
   */
  public ApiKeyService(ApiKeyRepository repo) {
    this.repo = repo;
  }

  /**
   * Represents the result of creating a new API key.
   *
   * <p>The {@code CreatedKey} record encapsulates the following: - The persisted {@link ApiKey}
   * entity. - The raw API key string which is only returned at the time of creation and cannot be
   * retrieved again.
   *
   * <p>This record is typically used as the result of the {@link ApiKeyService#create} method.
   *
   * @param entity The persisted {@link ApiKey} instance containing metadata and hashed key details.
   * @param rawKey The raw API key string, prefixed with {@code tsk_} and comprising 36 characters.
   */
  public record CreatedKey(ApiKey entity, String rawKey) {}

  /**
   * Creates a new API key with the specified parameters, stores it in the repository, and returns
   * the created key along with its raw representation.
   *
   * @param name the name of the API key; must not be null or blank
   * @param owner the identifier of the API key owner
   * @param scopes the list of scopes granted to the API key; if null, default scopes will be used
   * @param rateLimitRpm the allowed requests per minute for the API key; defaults to 60 if 0 or
   *     negative
   * @param expiresAt the expiration timestamp of the API key; can be null for no expiration
   * @return a {@code CreatedKey} object containing the persisted {@link ApiKey} entity and the raw
   *     key
   * @throws IllegalArgumentException if the name is null or blank, or if any scope in the list is
   *     invalid
   */
  public CreatedKey create(
      String name, String owner, List<String> scopes, int rateLimitRpm, Instant expiresAt) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("API key name is required.");
    }
    if (scopes != null) {
      for (String s : scopes) {
        if (!ALL_SCOPES.contains(s)) {
          throw new IllegalArgumentException("Invalid scope: " + s);
        }
      }
    }

    final byte[] raw = new byte[RAW_BYTES];
    rng.nextBytes(raw);
    final String hex = hexEncode(raw);
    final String rawKey = KEY_PREFIX + hex;

    var entity = new ApiKey();
    entity.setName(name);
    entity.setOwner(owner);
    entity.setKeyPrefix(hex.substring(0, PREFIX_LEN));
    entity.setKeyHash(sha256Hex(rawKey));
    entity.setScopes(scopes != null ? scopes : DEFAULT_SCOPES);
    entity.setRateLimitRpm(rateLimitRpm > 0 ? rateLimitRpm : 60);
    entity.setExpiresAt(expiresAt);

    entity = repo.save(entity);
    log.info("Created API key '{}' for owner '{}' (id={})", name, owner, entity.getId());

    return new CreatedKey(entity, rawKey);
  }

  /**
   * Validates a raw API key by checking its format, computing its hash, and verifying it against
   * stored keys in the repository. If a match is found and the key is usable, its usage is
   * recorded, and the key is returned.
   *
   * @param rawKey the raw API key string to validate; must include the proper prefix and adhere to
   *     the expected format
   * @return an {@code Optional} containing the validated and active {@link ApiKey} if the key is
   *     valid and usable; otherwise, an empty {@code Optional}
   */
  @Transactional
  public Optional<ApiKey> validate(String rawKey) {
    if (rawKey == null || !rawKey.startsWith(KEY_PREFIX)) {
      return Optional.empty();
    }

    final String hex = rawKey.substring(KEY_PREFIX.length());
    if (hex.length() < PREFIX_LEN) return Optional.empty();

    final String prefix = hex.substring(0, PREFIX_LEN);
    final String hash = sha256Hex(rawKey);

    final var candidates = repo.findByKeyPrefixAndActiveTrue(prefix);
    for (var key : candidates) {
      if (key.getKeyHash().equals(hash) && key.isUsable()) {
        repo.recordUsage(key.getId(), Instant.now());
        return Optional.of(key);
      }
    }

    return Optional.empty();
  }

  /**
   * Retrieves all API keys from the repository, ordered by their creation timestamp in descending
   * order.
   *
   * @return a list of all {@link ApiKey} entities, sorted by their creation date in descending
   *     order
   */
  @Transactional(readOnly = true)
  public List<ApiKey> listAll() {
    return repo.findAllByOrderByCreatedAtDesc();
  }

  /**
   * Retrieves a list of API keys associated with the specified owner, ordered by creation date in
   * descending order.
   *
   * @param owner the identifier of the owner whose API keys are to be retrieved
   * @return a list of API keys belonging to the specified owner, sorted by their creation date in
   *     descending order
   */
  @Transactional(readOnly = true)
  public List<ApiKey> listByOwner(String owner) {
    return repo.findByOwnerOrderByCreatedAtDesc(owner);
  }

  /**
   * Retrieves an API key by its unique identifier.
   *
   * @param id the unique identifier of the API key to be retrieved
   * @return an {@code Optional} containing the found {@code ApiKey}, or an empty {@code Optional}
   *     if no API key is found
   */
  @Transactional(readOnly = true)
  public Optional<ApiKey> findById(UUID id) {
    return repo.findById(id);
  }

  /**
   * Revokes an API key by deactivating it and marking it as revoked.
   *
   * @param id the unique identifier of the API key to be revoked
   */
  public void revoke(UUID id) {
    final var key =
        repo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("API key not found: " + id));
    key.setActive(false);
    key.setRevokedAt(Instant.now());
    repo.save(key);
    log.info("Revoked API key '{}' (id={})", key.getName(), id);
  }

  /**
   * Deletes an entity identified by the specified UUID. If the entity does not exist, an {@code
   * IllegalArgumentException} is thrown.
   *
   * @param id the UUID of the entity to be deleted
   * @throws IllegalArgumentException if the entity with the specified UUID is not found
   */
  public void delete(UUID id) {
    if (!repo.existsById(id)) {
      throw new IllegalArgumentException("API key not found: " + id);
    }
    repo.deleteById(id);
    log.info("Deleted API key id={}", id);
  }

  /**
   * Updates the scopes of an existing API key identified by its ID.
   *
   * @param id the unique identifier of the API key to update
   * @param scopes the list of scopes to be assigned to the API key
   * @return the updated API key with the new scopes
   * @throws IllegalArgumentException if the API key is not found or if any scope in the provided
   *     list is invalid
   */
  public ApiKey updateScopes(UUID id, List<String> scopes) {
    final var key =
        repo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("API key not found: " + id));
    for (String s : scopes) {
      if (!ALL_SCOPES.contains(s)) {
        throw new IllegalArgumentException("Invalid scope: " + s);
      }
    }
    key.setScopes(scopes);
    return repo.save(key);
  }

  /**
   * Updates the rate limit for the given API key.
   *
   * @param id the unique identifier of the API key to be updated
   * @param rpm the new rate limit in requests per minute
   * @return the updated ApiKey object with the new rate limit applied
   * @throws IllegalArgumentException if the API key with the specified ID is not found
   */
  public ApiKey updateRateLimit(UUID id, int rpm) {
    final var key =
        repo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("API key not found: " + id));
    key.setRateLimitRpm(rpm);
    return repo.save(key);
  }

  private static String sha256Hex(String input) {
    try {
      final MessageDigest md = MessageDigest.getInstance("SHA-256");
      final byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return hexEncode(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }

  private static String hexEncode(byte[] bytes) {
    final var sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
