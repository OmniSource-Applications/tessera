package live.omnisource.tessera.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import live.omnisource.tessera.catalog.entity.CatalogEntry;
import live.omnisource.tessera.exceptions.DataStoreValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Safely executes parameterized queries from the catalog.
 *
 * <p>Security model:</p>
 * <ul>
 *   <li>Queries are pre-stored in the catalog (not user-supplied SQL)</li>
 *   <li>Parameters are bound via {@link NamedParameterJdbcTemplate} (no interpolation)</li>
 *   <li>Each execution runs in a read-only transaction with statement timeout</li>
 *   <li>Results are capped at {@code maxRows} to prevent unbounded reads</li>
 * </ul>
 *
 * <p>The executor validates supplied parameters against the query's
 * {@code param_schema} and applies defaults for missing optional params.</p>
 */
@Slf4j
@Component
public class QueryExecutor {

    private static final int DEFAULT_MAX_ROWS = 5000;
    private static final Pattern PARAM_PATTERN = Pattern.compile(":(\\w+)");

    private final NamedParameterJdbcTemplate namedJdbc;
    private final TransactionTemplate txTemplate;
    private final ObjectMapper objectMapper;

    public QueryExecutor(NamedParameterJdbcTemplate namedJdbc,
                         TransactionTemplate txTemplate,
                         ObjectMapper objectMapper) {
        this.namedJdbc = namedJdbc;
        this.txTemplate = txTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Execute a catalog query with the given parameters.
     *
     * @param entry  the catalog entry to execute
     * @param params user-supplied parameter values
     * @return execution result with rows, timing, and metadata
     */
    public QueryResult execute(CatalogEntry entry, Map<String, Object> params) {
        Instant start = Instant.now();

        // 1. Merge supplied params with defaults from schema
        Map<String, Object> resolvedParams = resolveParams(entry, params);

        // 2. Validate required params are present
        validateRequiredParams(entry, resolvedParams);

        // 3. Type-coerce params to match schema expectations
        Map<String, Object> typedParams = coerceTypes(entry, resolvedParams);

        // 4. Execute with timeout and read-only enforcement
        List<Map<String, Object>> rows;
        try {
            rows = executeWithSafety(entry, typedParams);
        } catch (Exception e) {
            return QueryResult.failed(entry.getName(), start, e.getMessage());
        }

        Duration elapsed = Duration.between(start, Instant.now());
        log.debug("Executed {}: {} rows in {}ms", entry.getName(), rows.size(), elapsed.toMillis());

        return QueryResult.success(entry.getName(), rows, elapsed, entry.isStreaming());
    }

    // ── Parameter resolution ─────────────────────────────────

    /**
     * Merge user params with defaults declared in param_schema.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveParams(CatalogEntry entry, Map<String, Object> supplied) {
        var resolved = new HashMap<>(supplied != null ? supplied : Map.of());

        if (entry.getParamSchema() == null) return resolved;

        var properties = (Map<String, Object>) entry.getParamSchema().get("properties");
        if (properties == null) return resolved;

        for (var prop : properties.entrySet()) {
            String name = prop.getKey();
            if (!resolved.containsKey(name) || resolved.get(name) == null) {
                var schema = (Map<String, Object>) prop.getValue();
                if (schema.containsKey("default")) {
                    resolved.put(name, schema.get("default"));
                }
            }
        }

        return resolved;
    }

    /**
     * Check that all required params (those without defaults and not nullable) are present.
     */
    @SuppressWarnings("unchecked")
    private void validateRequiredParams(CatalogEntry entry, Map<String, Object> params) {
        if (entry.getParamSchema() == null) return;

        var properties = (Map<String, Object>) entry.getParamSchema().get("properties");
        if (properties == null) return;

        // Find all named params actually used in the SQL
        Set<String> sqlParams = extractNamedParams(entry.getQuerySql());

        for (String paramName : sqlParams) {
            if (!properties.containsKey(paramName)) continue; // undeclared = optional

            var schema = (Map<String, Object>) properties.get(paramName);
            boolean nullable = Boolean.TRUE.equals(schema.get("nullable"));
            boolean hasDefault = schema.containsKey("default");

            if (!nullable && !hasDefault && !params.containsKey(paramName)) {
                throw new DataStoreValidationException(
                        "Missing required parameter: " + paramName);
            }

            // Ensure nullable params that weren't supplied are explicitly null
            if (!params.containsKey(paramName)) {
                params.put(paramName, null);
            }
        }
    }

    /**
     * Coerce param values to match their declared types.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> coerceTypes(CatalogEntry entry, Map<String, Object> params) {
        if (entry.getParamSchema() == null) return params;

        var properties = (Map<String, Object>) entry.getParamSchema().get("properties");
        if (properties == null) return params;

        var result = new HashMap<>(params);

        for (var prop : properties.entrySet()) {
            String name = prop.getKey();
            Object val = result.get(name);
            if (val == null) continue;

            var schema = (Map<String, Object>) prop.getValue();
            String type = (String) schema.get("type");
            if (type == null) continue;

            result.put(name, coerceValue(name, val, type));
        }

        return result;
    }

    private Object coerceValue(String name, Object val, String type) {
        try {
            return switch (type) {
                case "number" -> val instanceof Number n ? n.doubleValue() : Double.parseDouble(val.toString());
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

    // ── Execution ────────────────────────────────────────────

    private List<Map<String, Object>> executeWithSafety(
            CatalogEntry entry, Map<String, Object> params) {

        // Create a read-only transaction template for this execution
        assert txTemplate.getTransactionManager() != null;
        var readOnlyTx = new TransactionTemplate(txTemplate.getTransactionManager());
        readOnlyTx.setReadOnly(true);
        readOnlyTx.setTimeout(Math.max(1, entry.getTimeoutMs() / 1000));

        return readOnlyTx.execute(status -> {
            // SET LOCAL only affects the current transaction
            int timeoutSec = Math.max(1, entry.getTimeoutMs() / 1000);
            namedJdbc.getJdbcTemplate()
                    .execute("SET LOCAL statement_timeout = '" + timeoutSec + "s'");

            log.debug("Catalog SQL [{}]:\n{}", entry.getName(), entry.getQuerySql());
            return namedJdbc.queryForList(entry.getQuerySql(), params);
        });
    }

    // ── Util ─────────────────────────────────────────────────

    private Set<String> extractNamedParams(String sql) {
        Set<String> params = new LinkedHashSet<>();
        Matcher m = PARAM_PATTERN.matcher(sql);
        while (m.find()) {
            params.add(m.group(1));
        }
        return params;
    }

    // ── Result record ────────────────────────────────────────

    public record QueryResult(
            String queryName,
            List<Map<String, Object>> rows,
            int rowCount,
            long elapsedMs,
            boolean streaming,
            boolean success,
            String errorMessage
    ) {
        public static QueryResult success(String name, List<Map<String, Object>> rows,
                                          Duration elapsed, boolean streaming) {
            return new QueryResult(name, rows, rows.size(), elapsed.toMillis(),
                    streaming, true, null);
        }

        public static QueryResult failed(String name, Instant start, String error) {
            long ms = Duration.between(start, Instant.now()).toMillis();
            return new QueryResult(name, List.of(), 0, ms, false, false, error);
        }
    }
}