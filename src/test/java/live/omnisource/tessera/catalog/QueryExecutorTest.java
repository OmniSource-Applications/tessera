package live.omnisource.tessera.catalog;

import live.omnisource.tessera.catalog.entity.CatalogEntry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QueryExecutorTest {

    @Test
    void execute_coercesTypes_appliesDefaults_andBindsNullables() {
        // Arrange
        var jdbcTemplate = mock(JdbcTemplate.class);
        var named = mock(NamedParameterJdbcTemplate.class);
        when(named.getJdbcTemplate()).thenReturn(jdbcTemplate);
        when(named.queryForList(anyString(), anyMap())).thenReturn(List.of(Map.of("ok", true)));

        var tx = new TransactionTemplate(new ResourcelessTransactionManager());
        var executor = new QueryExecutor(named, tx, new com.fasterxml.jackson.databind.ObjectMapper());

        var entry = new CatalogEntry();
        entry.setName("features.by_bbox");
        entry.setTimeoutMs(30_000);
        entry.setStreaming(false);
        entry.setQuerySql("select * from t where a=:a and (:b is null or b=:b) limit :limit offset :offset");
        entry.setParamSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                        "a", Map.of("type", "integer"),
                        "b", Map.of("type", "string", "nullable", true),
                        "limit", Map.of("type", "integer", "default", 1000),
                        "offset", Map.of("type", "integer", "default", 0)
                )
        ));

        // b omitted on purpose; limit/offset omitted to exercise defaults
        var params = Map.<String, Object>of(
                "a", "42"
        );

        // Act
        var result = executor.execute(entry, params);

        // Assert
        assertThat(result.success()).isTrue();
        assertThat(result.rowCount()).isEqualTo(1);

        // Verify SET LOCAL timeout executes
        verify(jdbcTemplate).execute(startsWith("SET LOCAL statement_timeout"));

        // Verify named parameters were passed with coercion/defaults/nullables
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(named).queryForList(eq(entry.getQuerySql()), captor.capture());
        var bound = captor.getValue();

        assertThat(bound.get("a")).isEqualTo(42);
        assertThat(bound).containsEntry("limit", 1000);
        assertThat(bound).containsEntry("offset", 0);
        assertThat(bound).containsEntry("b", null);
    }

    @Test
    void execute_failsWhenRequiredParamMissing() {
        var jdbcTemplate = mock(JdbcTemplate.class);
        var named = mock(NamedParameterJdbcTemplate.class);
        when(named.getJdbcTemplate()).thenReturn(jdbcTemplate);
        var tx = new TransactionTemplate(new ResourcelessTransactionManager());
        var executor = new QueryExecutor(named, tx, new com.fasterxml.jackson.databind.ObjectMapper());

        var entry = new CatalogEntry();
        entry.setName("q");
        entry.setTimeoutMs(1000);
        entry.setStreaming(false);
        entry.setQuerySql("select 1 where x=:x");
        entry.setParamSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                        "x", Map.of("type", "integer")
                )
        ));

        var result = executor.execute(entry, Map.of());
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Missing required parameter");
    }

    @Test
    void execute_failsWhenTypeCoercionInvalid() {
        var jdbcTemplate = mock(JdbcTemplate.class);
        var named = mock(NamedParameterJdbcTemplate.class);
        when(named.getJdbcTemplate()).thenReturn(jdbcTemplate);
        var tx = new TransactionTemplate(new ResourcelessTransactionManager());
        var executor = new QueryExecutor(named, tx, new com.fasterxml.jackson.databind.ObjectMapper());

        var entry = new CatalogEntry();
        entry.setName("q");
        entry.setTimeoutMs(1000);
        entry.setStreaming(false);
        entry.setQuerySql("select 1 where x=:x");
        entry.setParamSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                        "x", Map.of("type", "integer")
                )
        ));

        var result = executor.execute(entry, Map.of("x", "not-an-int"));
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Invalid value for parameter 'x'");
    }
}
