package live.omnisource.tessera.apikey.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import live.omnisource.tessera.apikey.entity.ApiKey;

/**
 * Repository interface for managing and querying {@code ApiKey} entities. Provides methods for
 * retrieving, updating, and interacting with API key records in the underlying database. This
 * interface extends {@code JpaRepository} to provide CRUD operations and allows for custom queries
 * as defined. <br>
 * </br> Query Methods: - {@code findByKeyPrefixAndActiveTrue(String keyPrefix)}: Retrieves a list
 * of active API keys with the specified key prefix. - {@code findByOwnerOrderByCreatedAtDesc(String
 * owner)}: Retrieves a list of API keys owned by the specified owner, ordered by creation date in
 * descending order. - {@code findAllByOrderByCreatedAtDesc()}: Retrieves all API keys, ordered by
 * creation date in descending order. <br>
 * </br> Custom Update Query: - {@code recordUsage(UUID id, Instant now)}: Updates the last used
 * timestamp and increments the request count for the specified API key. <br>
 * </br> This repository leverages Spring Data JPA, enabling seamless database interactions with the
 * {@code ApiKey} entity.
 */
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

  /**
   * Retrieves a list of active API keys that have the specified key prefix. <br>
   * </br>
   *
   * @param keyPrefix the prefix of the API key used to filter the results.
   * @return a list of {@code ApiKey} objects that match the given key prefix and are active.
   */
  List<ApiKey> findByKeyPrefixAndActiveTrue(String keyPrefix);

  /**
   * Retrieves a list of API keys owned by the specified owner, ordered by their creation timestamp
   * in descending order. <br>
   * </br>
   *
   * @param owner the identifier of the entity or user that owns the API keys.
   * @return a list of {@code ApiKey} objects associated with the specified owner, sorted by the
   *     {@code createdAt} field in descending order.
   */
  List<ApiKey> findByOwnerOrderByCreatedAtDesc(String owner);

  /**
   * Retrieves all API keys from the database, ordered by their creation timestamp in descending
   * order. <br>
   * </br>
   *
   * @return a list of {@code ApiKey} objects sorted by the {@code createdAt} field in descending
   *     order.
   */
  List<ApiKey> findAllByOrderByCreatedAtDesc();

  /**
   * Updates the last used timestamp and increments the request count for the specified API key.
   *
   * @param id the unique identifier of the API key to update.
   * @param now the current timestamp to set as the last used time.
   */
  @Modifying
  @Query(
      "UPDATE ApiKey k SET k.lastUsedAt = :now, k.requestCount = k.requestCount + 1 WHERE k.id = :id")
  void recordUsage(UUID id, Instant now);
}
