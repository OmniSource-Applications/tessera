package live.omnisource.tessera.datasource.introspection;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;

import live.omnisource.tessera.layer.dto.IntrospectionResult;
import live.omnisource.tessera.layer.dto.IntrospectionResult.SpatialTable;
import lombok.extern.slf4j.Slf4j;

/**
 * MySQL spatial table discovery.
 *
 * <p>Queries information_schema.COLUMNS for columns with spatial data types (geometry, point,
 * linestring, polygon, etc.).
 */
@Slf4j
public final class MysqlIntrospector {

  private MysqlIntrospector() {}

  private static final Set<String> GEO_TYPES =
      Set.of(
          "geometry",
          "point",
          "linestring",
          "polygon",
          "multipoint",
          "multilinestring",
          "multipolygon",
          "geometrycollection");

  private static final String DISCOVER_SQL =
      """
            SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, DATA_TYPE
            FROM information_schema.COLUMNS
            WHERE DATA_TYPE IN ('geometry','point','linestring','polygon',
                                'multipoint','multilinestring','multipolygon','geometrycollection')
              AND TABLE_SCHEMA NOT IN ('mysql','information_schema','performance_schema','sys')
            ORDER BY TABLE_SCHEMA, TABLE_NAME, ORDINAL_POSITION
            """;

  public static IntrospectionResult introspect(DataSource dataSource) {
    List<SpatialTable> tables = new ArrayList<>();

    try (Connection c = dataSource.getConnection();
        Statement st = c.createStatement();
        ResultSet rs = st.executeQuery(DISCOVER_SQL)) {

      while (rs.next()) {
        String schema = rs.getString("TABLE_SCHEMA");
        String table = rs.getString("TABLE_NAME");
        String geomCol = rs.getString("COLUMN_NAME");
        String geomType = rs.getString("DATA_TYPE").toUpperCase();

        long rowCount = fetchRowCount(c, schema, table);

        // MySQL doesn't have a simple SRID metadata view prior to 8.0.23;
        // default to 4326 (WGS84) as the common case for geo data
        tables.add(new SpatialTable(schema, table, geomCol, geomType, 4326, rowCount, null));
      }

    } catch (SQLException e) {
      log.error("MySQL introspection failed: {}", e.getMessage(), e);
      throw new RuntimeException("Introspection failed: " + e.getMessage(), e);
    }

    log.info("MySQL introspection: discovered {} spatial tables", tables.size());
    return new IntrospectionResult(tables);
  }

  private static long fetchRowCount(Connection c, String schema, String table) {
    String sql =
        "SELECT COUNT(*) FROM `"
            + schema.replace("`", "``")
            + "`.`"
            + table.replace("`", "``")
            + "`";
    try (Statement st = c.createStatement();
        ResultSet rs = st.executeQuery(sql)) {
      rs.next();
      return rs.getLong(1);
    } catch (SQLException e) {
      log.warn("Row count failed for {}.{}: {}", schema, table, e.getMessage());
      return -1;
    }
  }
}
