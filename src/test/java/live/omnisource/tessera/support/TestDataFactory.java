package live.omnisource.tessera.support;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import live.omnisource.tessera.apikey.entity.ApiKey;
import live.omnisource.tessera.catalog.entity.CatalogEntry;
import live.omnisource.tessera.model.dto.ExternalSourceCredentials;

/**
 * Factory for building test objects with sensible defaults. Each builder method returns a fully
 * populated object that can be overridden as needed.
 */
public final class TestDataFactory {

  private TestDataFactory() {}

  // ── Catalog Entries ─────────────────────────────────

  public static CatalogEntry catalogEntry(String name) {
    var entry = new CatalogEntry();
    entry.setName(name);
    entry.setDescription("Test query: " + name);
    entry.setCategory("CUSTOM");
    entry.setQuerySql("SELECT 1 AS result");
    entry.setTimeoutMs(5000);
    entry.setStreaming(false);
    entry.setTags(List.of("test"));
    entry.setParamSchema(Map.of("type", "object", "properties", Map.of()));
    return entry;
  }

  public static CatalogEntry spatialCatalogEntry(String name) {
    var entry = catalogEntry(name);
    entry.setCategory("STATIC");
    entry.setQuerySql(
        """
                SELECT f.id, f.external_id, ST_AsGeoJSON(f.geometry)::jsonb AS geometry
                FROM tessera.geo_features f
                WHERE ST_Intersects(f.geometry, ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326))
                LIMIT :limit
                """);
    entry.setParamSchema(
        Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                "minLon", Map.of("type", "number"),
                "minLat", Map.of("type", "number"),
                "maxLon", Map.of("type", "number"),
                "maxLat", Map.of("type", "number"),
                "limit", Map.of("type", "integer", "default", 100))));
    entry.setTags(List.of("spatial", "test"));
    return entry;
  }

  // ── API Keys ────────────────────────────────────────

  public static ApiKey apiKey(String name, String owner) {
    var key = new ApiKey();
    key.setId(UUID.randomUUID());
    key.setName(name);
    key.setOwner(owner);
    key.setKeyPrefix("abcd1234");
    key.setKeyHash("fakehash_" + UUID.randomUUID());
    key.setScopes(List.of("QUERY_READ", "QUERY_EXECUTE"));
    key.setRateLimitRpm(60);
    key.setActive(true);
    key.setRequestCount(0);
    return key;
  }

  // ── External Source Credentials ─────────────────────

  public static ExternalSourceCredentials postgisCredentials() {
    return new ExternalSourceCredentials(
        "POSTGIS",
        "jdbc:postgresql://localhost:5432/testdb",
        "testuser",
        "testpass",
        null,
        4,
        null,
        0,
        null,
        null,
        null,
        true);
  }

  public static ExternalSourceCredentials cassandraCredentials() {
    return new ExternalSourceCredentials(
        "CASSANDRA",
        null,
        "cassandra",
        "cassandra",
        null,
        0,
        "localhost",
        9042,
        "datacenter1",
        "test_ks",
        null,
        true);
  }

  public static ExternalSourceCredentials mysqlCredentials() {
    return new ExternalSourceCredentials(
        "MYSQL",
        "jdbc:mysql://localhost:3306/testdb",
        "root",
        "root",
        null,
        4,
        null,
        0,
        null,
        null,
        null,
        true);
  }

  public static ExternalSourceCredentials elasticsearchCredentials() {
    return new ExternalSourceCredentials(
        "ELASTICSEARCH",
        null,
        "elastic",
        "elastic",
        null,
        0,
        null,
        0,
        null,
        null,
        "https://localhost:9200",
        true);
  }
}
