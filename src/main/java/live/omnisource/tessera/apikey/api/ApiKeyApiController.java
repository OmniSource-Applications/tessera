package live.omnisource.tessera.apikey.api;

import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import live.omnisource.tessera.apikey.ApiKeyFeatureToggle;
import live.omnisource.tessera.apikey.ApiKeyService;
import live.omnisource.tessera.apikey.entity.ApiKey;

/**
 * REST API for managing API keys programmatically.
 *
 * <pre>
 *   POST   /api/keys            — create a new key (raw key returned once)
 *   GET    /api/keys            — list keys for the current user
 *   GET    /api/keys/{id}       — get key details (no secret)
 *   DELETE /api/keys/{id}       — revoke a key
 * </pre>
 *
 * <p>All endpoints return 404 when the API key feature is disabled.
 */
@RestController
@RequestMapping("/api/keys")
public class ApiKeyApiController {

  private final ApiKeyService apiKeyService;
  private final ApiKeyFeatureToggle featureToggle;

  /**
   * Constructs an instance of ApiKeyApiController with the specified dependencies.
   *
   * @param apiKeyService the service responsible for managing the lifecycle of API keys, such as
   *     creation, retrieval, listing, and revocation
   * @param featureToggle the feature toggle used to enable or disable API key functionality across
   *     the application
   */
  public ApiKeyApiController(ApiKeyService apiKeyService, ApiKeyFeatureToggle featureToggle) {
    this.apiKeyService = apiKeyService;
    this.featureToggle = featureToggle;
  }

  /**
   * Handles the creation of a new API key. This method generates a new API key based on the
   * provided request payload and returns the key with its associated metadata. The raw key is only
   * returned in the response and will not be stored or retrievable again. If the API key feature is
   * disabled, the endpoint will return a 404 response.
   *
   * @param req the request payload containing the details for the new API key, such as its name,
   *     scopes, rate limit, and optional expiration period in days
   * @param principal the security principal representing the authenticated user making the request
   * @return a ResponseEntity containing the newly created API key details upon success, or an error
   *     response if the request is invalid or if the API key feature is disabled
   */
  @PostMapping
  public ResponseEntity<Map<String, Object>> create(
      @RequestBody CreateRequest req, Principal principal) {
    if (!featureToggle.isEnabled()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", "API key feature is disabled."));
    }
    try {
      Instant expiresAt = null;
      if (req.expiresInDays != null && req.expiresInDays > 0) {
        expiresAt = Instant.now().plus(req.expiresInDays, ChronoUnit.DAYS);
      }

      final var result =
          apiKeyService.create(
              req.name,
              principal.getName(),
              req.scopes,
              req.rateLimitRpm != null ? req.rateLimitRpm : 60,
              expiresAt);

      final var body = new LinkedHashMap<String, Object>();
      body.put("id", result.entity().getId());
      body.put("name", result.entity().getName());
      body.put("key", result.rawKey());
      body.put("scopes", result.entity().getScopes());
      body.put("rateLimitRpm", result.entity().getRateLimitRpm());
      body.put("expiresAt", result.entity().getExpiresAt());
      body.put("warning", "Store this key securely. It will not be shown again.");

      return ResponseEntity.status(HttpStatus.CREATED).body(body);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * Retrieves a list of API keys for the authenticated user. The method checks if the feature
   * toggle for API key management is enabled, and returns the list of API keys or an appropriate
   * error response if the feature is disabled.
   *
   * @param principal the security principal representing the authenticated user making the request
   * @return a ResponseEntity containing a list of API keys associated with the user upon success,
   *     or a 404 error response if the API key feature is disabled
   */
  @GetMapping
  public ResponseEntity<?> list(Principal principal) {
    if (!featureToggle.isEnabled()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", "API key feature is disabled."));
    }
    return ResponseEntity.ok(apiKeyService.listAll().stream().map(KeySummary::from).toList());
  }

  /**
   * Retrieves the details of an API key by its unique identifier (UUID). If the API key feature is
   * disabled, the endpoint returns a 404 status with an appropriate error message.
   *
   * @param id the unique identifier of the API key
   * @return a ResponseEntity containing the API key details as a KeySummary object if the key
   *     exists, a 404 status if the key does not exist, or a 404 status with an error message if
   *     the API key feature is disabled
   */
  @GetMapping("/{id}")
  public ResponseEntity<?> getById(@PathVariable UUID id) {
    if (!featureToggle.isEnabled()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", "API key feature is disabled."));
    }
    return apiKeyService
        .findById(id)
        .map(k -> ResponseEntity.ok((Object) KeySummary.from(k)))
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Revokes an API key identified by the provided unique identifier (UUID). If the API key feature
   * toggle is disabled, the method returns a 404 status with an appropriate error message. If the
   * API key does not exist, it returns a 404 status. Upon successful revocation, it responds with a
   * confirmation message.
   *
   * @param id the unique identifier of the API key to be revoked
   * @return a ResponseEntity containing a confirmation of revocation with the API key ID, a 404
   *     status if the API key does not exist or if the feature toggle is disabled, or a 404 status
   *     with an error message if the feature is unavailable
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Map<String, Object>> revoke(@PathVariable UUID id) {
    if (!featureToggle.isEnabled()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", "API key feature is disabled."));
    }
    try {
      apiKeyService.revoke(id);
      return ResponseEntity.ok(Map.of("revoked", true, "id", id.toString()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.notFound().build();
    }
  }

  record CreateRequest(
      String name, List<String> scopes, Integer rateLimitRpm, Integer expiresInDays) {}

  record KeySummary(
      UUID id,
      String name,
      String keyPrefix,
      String owner,
      List<String> scopes,
      int rateLimitRpm,
      boolean active,
      Instant lastUsedAt,
      long requestCount,
      Instant expiresAt,
      Instant createdAt,
      Instant revokedAt) {
    static KeySummary from(ApiKey k) {
      return new KeySummary(
          k.getId(),
          k.getName(),
          "tsk_" + k.getKeyPrefix() + "…",
          k.getOwner(),
          k.getScopes(),
          k.getRateLimitRpm(),
          k.isActive(),
          k.getLastUsedAt(),
          k.getRequestCount(),
          k.getExpiresAt(),
          k.getCreatedAt(),
          k.getRevokedAt());
    }
  }
}
