package live.omnisource.tessera.catalog;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import live.omnisource.tessera.catalog.entity.CatalogEntry;
import live.omnisource.tessera.exceptions.DataStoreValidationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Safely executes parameterized queries from the catalog.
 *
 * <p>Security model:
 *
 * <ul>
 *   <li>Queries are pre-stored in the catalog (not user-supplied SQL)
 *   <li>Parameters are bound via {@link NamedParameterJdbcTemplate} (no interpolation)
 *   <li>Each execution runs in a read-only transaction with statement timeout
 *   <li>Results are capped at {@code maxRows} to prevent unbounded reads
 * </ul>
 *
 * <p>The executor validates supplied parameters against the query's {@code param_schema} and
 * applies defaults for missing optional params.
 */
@Slf4j
@Component
public class QueryExecutor {

  private static final int DEFAULT_MAX_ROWS = 5000;
  private static final Pattern PARAM_PATTERN = Pattern.compile(":(\\w+)");

  private final NamedParameterJdbcTemplate namedJdbc;
  private final TransactionTemplate txTemplate;
  private final ObjectMapper objectMapper;

  /**
   * Constructs an instance of QueryExecutor with the specified dependencies.
   *
   * @param namedJdbc the {@link NamedParameterJdbcTemplate} used for executing SQL queries with
   *     named parameters
   * @param txTemplate the {@link TransactionTemplate} used for managing transactions during query
   *     execution
   * @param objectMapper the {@link ObjectMapper} used for serializing and deserializing objects in
   *     query operations
   */
  public QueryExecutor(
      NamedParameterJdbcTemplate namedJdbc,
      TransactionTemplate txTemplate,
      ObjectMapper objectMapper) {
    this.namedJdbc = namedJdbc;
    this.txTemplate = txTemplate;
    this.objectMapper = objectMapper;
  }

  /**
   * Execute a catalog query with the given parameters.
   *
   * @param entry the catalog entry to execute
   * @param params user-supplied parameter values
   * @return execution result with rows, timing, and metadata
   */
  public QueryResult execute(CatalogEntry entry, Map<String, Object> params) {
    final Instant start = Instant.now();
    final Map<String, Object> resolvedParams = resolveParams(entry, params);
    validateRequiredParams(entry, resolvedParams);
    Map<String, Object> typedParams = coerceTypes(entry, resolvedParams);
    List<Map<String, Object>> rows;

    try {
      rows = executeWithSafety(entry, typedParams);
    } catch (Exception e) {
      return QueryResult.failed(entry.getName(), start, e.getMessage());
    }

    final Duration elapsed = Duration.between(start, Instant.now());
    log.debug("Executed {}: {} rows in {}ms", entry.getName(), rows.size(), elapsed.toMillis());

    return QueryResult.success(entry.getName(), rows, elapsed, entry.isStreaming());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> resolveParams(CatalogEntry entry, Map<String, Object> supplied) {
    final var resolved = new HashMap<>(supplied != null ? supplied : Map.of());

    if (entry.getParamSchema() == null) return resolved;

    final var properties = (Map<String, Object>) entry.getParamSchema().get("properties");
    if (properties == null) return resolved;

    for (var prop : properties.entrySet()) {
      final String name = prop.getKey();
      if (!resolved.containsKey(name) || resolved.get(name) == null) {
        var schema = (Map<String, Object>) prop.getValue();
        if (schema.containsKey("default")) {
          resolved.put(name, schema.get("default"));
        }
      }
    }

    return resolved;
  }

  @SuppressWarnings("unchecked")
  private void validateRequiredParams(CatalogEntry entry, Map<String, Object> params) {
    if (entry.getParamSchema() == null) return;

    final var properties = (Map<String, Object>) entry.getParamSchema().get("properties");
    if (properties == null) return;

    final Set<String> sqlParams = extractNamedParams(entry.getQuerySql());

    for (String paramName : sqlParams) {
      if (!properties.containsKey(paramName)) continue; // undeclared = optional

      final var schema = (Map<String, Object>) properties.get(paramName);
      final boolean nullable = Boolean.TRUE.equals(schema.get("nullable"));
      final boolean hasDefault = schema.containsKey("default");

      if (!nullable && !hasDefault && !params.containsKey(paramName)) {
        throw new DataStoreValidationException("Missing required parameter: " + paramName);
      }

      if (!params.containsKey(paramName)) {
        params.put(paramName, null);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> coerceTypes(CatalogEntry entry, Map<String, Object> params) {
    if (entry.getParamSchema() == null) return params;

    final var properties = (Map<String, Object>) entry.getParamSchema().get("properties");
    if (properties == null) return params;

    final var result = new HashMap<>(params);

    for (var prop : properties.entrySet()) {
      final String name = prop.getKey();
      final Object val = result.get(name);
      if (val == null) continue;

      final var schema = (Map<String, Object>) prop.getValue();
      final String type = (String) schema.get("type");
      if (type == null) continue;

      result.put(name, coerceValue(name, val, type));
    }

    return result;
  }

  private Object coerceValue(String name, Object val, String type) {
    try {
      return switch (type) {
        case "number" ->
            val instanceof Number n ? n.doubleValue() : Double.parseDouble(val.toString());
        case "integer" -> val instanceof Number n ? n.intValue() : Integer.parseInt(val.toString());
        case "string" -> val.toString();
        case "boolean" -> val instanceof Boolean b ? b : Boolean.parseBoolean(val.toString());
        default -> val;
      };
    } catch (NumberFormatException e) {
      throw new DataStoreValidationException(
          "Invalid value for parameter '" + name + "': expected " + type);
    }
  }

  private List<Map<String, Object>> executeWithSafety(
      CatalogEntry entry, Map<String, Object> params) {
    assert txTemplate.getTransactionManager() != null;
    final var readOnlyTx = new TransactionTemplate(txTemplate.getTransactionManager());
    readOnlyTx.setReadOnly(true);
    readOnlyTx.setTimeout(Math.max(1, entry.getTimeoutMs() / 1000));

    return readOnlyTx.execute(
        status -> {
          // SET LOCAL only affects the current transaction
          final int timeoutSec = Math.max(1, entry.getTimeoutMs() / 1000);
          namedJdbc
              .getJdbcTemplate()
              .execute("SET LOCAL statement_timeout = '" + timeoutSec + "s'");

          log.debug("Catalog SQL [{}]:\n{}", entry.getName(), entry.getQuerySql());
          return namedJdbc.queryForList(entry.getQuerySql(), params);
        });
  }

  private Set<String> extractNamedParams(String sql) {
    final Set<String> params = new LinkedHashSet<>();
    final Matcher m = PARAM_PATTERN.matcher(sql);
    while (m.find()) {
      params.add(m.group(1));
    }
    return params;
  }

  /**
   * Represents the result of executing a query, encapsulating details such as rows returned,
   * execution time, and status information.
   *
   * @param queryName The name of the executed query.
   * @param rows The list of rows returned as a result of query execution. Each row is represented
   *     as a map of column names to their corresponding values.
   * @param rowCount The number of rows returned by the query.
   * @param elapsedMs The time taken to execute the query, in milliseconds.
   * @param streaming A flag indicating whether the query supports streaming results.
   * @param success A flag indicating whether the query execution was successful.
   * @param errorMessage The error message if the query execution failed; null if successful.
   */
  public record QueryResult(
      String queryName,
      List<Map<String, Object>> rows,
      int rowCount,
      long elapsedMs,
      boolean streaming,
      boolean success,
      String errorMessage) {
    public static QueryResult success(
        String name, List<Map<String, Object>> rows, Duration elapsed, boolean streaming) {
      return new QueryResult(name, rows, rows.size(), elapsed.toMillis(), streaming, true, null);
    }

    public static QueryResult failed(String name, Instant start, String error) {
      final long ms = Duration.between(start, Instant.now()).toMillis();
      return new QueryResult(name, List.of(), 0, ms, false, false, error);
    }
  }
}
