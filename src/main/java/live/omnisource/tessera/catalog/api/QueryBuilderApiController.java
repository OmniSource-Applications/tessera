package live.omnisource.tessera.catalog.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides metadata about Tessera's internal schema tables for the visual query builder. Only
 * exposes known safe tables — never arbitrary user input.
 */
@RestController
@RequestMapping("/api/builder")
public class QueryBuilderApiController {

  private static final Set<String> ALLOWED_TABLES =
      Set.of(
          "geo_features",
          "h3_cell_index",
          "mv_h3_density_r7",
          "external_sources",
          "sync_checkpoints",
          "schema_metadata",
          "query_catalog");

  private final JdbcTemplate jdbc;

  /** Constructs an instance of {@code QueryBuilderApiController}. */
  public QueryBuilderApiController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** GET /api/builder/tables — list queryable tables with column info. */
  @GetMapping("/tables")
  public ResponseEntity<List<Map<String, Object>>> listTables() {
    final var result = new ArrayList<Map<String, Object>>();
    for (String table : ALLOWED_TABLES.stream().sorted().toList()) {
      final var cols = fetchColumns("tessera", table);
      if (!cols.isEmpty()) {
        result.add(
            Map.of(
                "schema",
                "tessera",
                "table",
                table,
                "qualified",
                "tessera." + table,
                "columns",
                cols));
      }
    }
    return ResponseEntity.ok(result);
  }

  /** GET /api/builder/tables/{table}/columns — columns for a specific table. */
  @GetMapping("/tables/{table}/columns")
  public ResponseEntity<?> tableColumns(@PathVariable String table) {
    if (!ALLOWED_TABLES.contains(table)) {
      return ResponseEntity.badRequest().body(Map.of("error", "Unknown table: " + table));
    }
    final var cols = fetchColumns("tessera", table);
    return ResponseEntity.ok(cols);
  }

  private List<Map<String, Object>> fetchColumns(String schema, String table) {
    final String sql =
        """
            SELECT c.column_name, c.data_type, c.udt_name, c.is_nullable,
                   EXISTS(
                     SELECT 1 FROM information_schema.table_constraints tc
                     JOIN information_schema.key_column_usage kcu
                          ON kcu.constraint_name = tc.constraint_name
                          AND kcu.table_schema = tc.table_schema
                     WHERE tc.constraint_type = 'PRIMARY KEY'
                       AND kcu.column_name = c.column_name
                       AND tc.table_name = ? AND tc.table_schema = ?
                   ) AS is_pk
            FROM information_schema.columns c
            WHERE c.table_schema = ? AND c.table_name = ?
            ORDER BY c.ordinal_position
            """;

    return jdbc.query(
        sql,
        (rs, i) -> {
          final var col = new LinkedHashMap<String, Object>();
          col.put("name", rs.getString("column_name"));
          col.put("dataType", rs.getString("data_type"));
          col.put("udtName", rs.getString("udt_name"));
          col.put("nullable", "YES".equals(rs.getString("is_nullable")));
          col.put("primaryKey", rs.getBoolean("is_pk"));
          col.put("isGeometry", isGeoType(rs.getString("udt_name")));
          return col;
        },
        table,
        schema,
        schema,
        table);
  }

  private boolean isGeoType(String udt) {
    return udt != null && (udt.startsWith("geometry") || udt.startsWith("geography"));
  }
}
