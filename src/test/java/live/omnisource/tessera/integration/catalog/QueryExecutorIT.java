package live.omnisource.tessera.integration.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import live.omnisource.tessera.catalog.QueryExecutor;
import live.omnisource.tessera.catalog.entity.CatalogEntry;
import live.omnisource.tessera.support.IntegrationTestBase;

class QueryExecutorIT extends IntegrationTestBase {

  @Autowired QueryExecutor executor;

  @Test
  void execute_simpleSelectReturnsResult() {
    var entry = new CatalogEntry();
    entry.setName("test.simple");
    entry.setQuerySql("SELECT 42 AS answer, 'hello' AS greeting");
    entry.setTimeoutMs(5000);

    var result = executor.execute(entry, Map.of());

    assertThat(result.success()).isTrue();
    assertThat(result.rowCount()).isEqualTo(1);
    assertThat(result.rows().getFirst().get("answer")).isEqualTo(42);
    assertThat(result.rows().getFirst().get("greeting")).isEqualTo("hello");
  }

  @Test
  void execute_parameterizedQuery() {
    var entry = new CatalogEntry();
    entry.setName("test.parameterized");
    entry.setQuerySql("SELECT :val::int AS doubled WHERE :val::int > 0");
    entry.setTimeoutMs(5000);
    entry.setParamSchema(
        Map.of("type", "object", "properties", Map.of("val", Map.of("type", "integer"))));

    var result = executor.execute(entry, Map.of("val", "10"));

    assertThat(result.success()).isTrue();
    assertThat(result.rowCount()).isEqualTo(1);
  }

  @Test
  void execute_appliesDefaultParams() {
    var entry = new CatalogEntry();
    entry.setName("test.defaults");
    entry.setQuerySql("SELECT :limit AS lim");
    entry.setTimeoutMs(5000);
    entry.setParamSchema(
        Map.of(
            "type",
            "object",
            "properties",
            Map.of("limit", Map.of("type", "integer", "default", 500))));

    var result = executor.execute(entry, Map.of());

    assertThat(result.success()).isTrue();
    assertThat(result.rows().getFirst().get("lim")).isEqualTo(500);
  }

  @Test
  void execute_seededBboxQueryWithNoData() {
    // Execute the seeded features.by_bbox query — no geo_features rows expected
    var entry = new CatalogEntry();
    entry.setName("test.bbox_empty");
    entry.setQuerySql(
        """
                SELECT count(*) AS cnt
                FROM tessera.geo_features f
                WHERE ST_Intersects(f.geometry, ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326))
                """);
    entry.setTimeoutMs(5000);
    entry.setParamSchema(
        Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                "minLon", Map.of("type", "number"),
                "minLat", Map.of("type", "number"),
                "maxLon", Map.of("type", "number"),
                "maxLat", Map.of("type", "number"))));

    var result =
        executor.execute(
            entry, Map.of("minLon", "-180", "minLat", "-90", "maxLon", "180", "maxLat", "90"));

    assertThat(result.success()).isTrue();
    assertThat(result.rows().getFirst().get("cnt")).isEqualTo(0L);
  }

  @Test
  void execute_badSqlReturnsFailure() {
    var entry = new CatalogEntry();
    entry.setName("test.bad_sql");
    entry.setQuerySql("SELECT * FROM this_table_does_not_exist_at_all");
    entry.setTimeoutMs(5000);

    var result = executor.execute(entry, Map.of());

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).isNotNull();
  }

  @Test
  void execute_typeCoercionWorks() {
    var entry = new CatalogEntry();
    entry.setName("test.coerce");
    entry.setQuerySql("SELECT :num::float AS f, :flag::boolean AS b");
    entry.setTimeoutMs(5000);
    entry.setParamSchema(
        Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                "num", Map.of("type", "number"),
                "flag", Map.of("type", "boolean"))));

    var result = executor.execute(entry, Map.of("num", "3.14", "flag", "true"));

    assertThat(result.success()).isTrue();
    assertThat((Number) result.rows().getFirst().get("f"))
        .satisfies(
            n ->
                assertThat(n.doubleValue())
                    .isCloseTo(3.14, org.assertj.core.data.Offset.offset(0.001)));
  }
}
