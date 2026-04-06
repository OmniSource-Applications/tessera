package live.omnisource.tessera.datasource.introspection;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import live.omnisource.tessera.layer.dto.IntrospectionResult;
import live.omnisource.tessera.layer.dto.IntrospectionResult.SpatialTable;
import lombok.extern.slf4j.Slf4j;

/**
 * Oracle Spatial table discovery.
 *
 * <p>Uses two strategies: 1. ALL_SDO_GEOM_METADATA — for tables with registered geometry metadata
 * 2. Fallback to scanning ALL_TAB_COLUMNS for SDO_GEOMETRY typed columns
 */
@Slf4j
public final class OracleIntrospector {

  private OracleIntrospector() {}

  /** Primary query: discover from SDO metadata registry. */
  private static final String SDO_METADATA_SQL =
      """
            SELECT OWNER, TABLE_NAME, COLUMN_NAME, SRID
            FROM ALL_SDO_GEOM_METADATA
            ORDER BY OWNER, TABLE_NAME
            """;

  /** Fallback: find SDO_GEOMETRY columns not registered in metadata. */
  private static final String SDO_COLUMN_FALLBACK_SQL =
      """
            SELECT c.OWNER, c.TABLE_NAME, c.COLUMN_NAME
            FROM ALL_TAB_COLUMNS c
            WHERE c.DATA_TYPE = 'SDO_GEOMETRY'
              AND NOT EXISTS (
                SELECT 1 FROM ALL_SDO_GEOM_METADATA m
                WHERE m.OWNER = c.OWNER AND m.TABLE_NAME = c.TABLE_NAME
                  AND m.COLUMN_NAME = c.COLUMN_NAME
              )
            ORDER BY c.OWNER, c.TABLE_NAME
            """;

  public static IntrospectionResult introspect(DataSource dataSource) {
    List<SpatialTable> tables = new ArrayList<>();

    try (Connection c = dataSource.getConnection()) {

      // Strategy 1: registered metadata
      try (Statement st = c.createStatement();
          ResultSet rs = st.executeQuery(SDO_METADATA_SQL)) {
        while (rs.next()) {
          String owner = rs.getString("OWNER");
          String table = rs.getString("TABLE_NAME");
          String geomCol = rs.getString("COLUMN_NAME");
          int srid = rs.getInt("SRID");
          if (rs.wasNull()) srid = 4326;

          long rowCount = fetchRowCount(c, owner, table);
          tables.add(new SpatialTable(owner, table, geomCol, "SDO_GEOMETRY", srid, rowCount, null));
        }
      }

      // Strategy 2: fallback for unregistered columns
      try (Statement st = c.createStatement();
          ResultSet rs = st.executeQuery(SDO_COLUMN_FALLBACK_SQL)) {
        while (rs.next()) {
          String owner = rs.getString("OWNER");
          String table = rs.getString("TABLE_NAME");
          String geomCol = rs.getString("COLUMN_NAME");

          long rowCount = fetchRowCount(c, owner, table);
          tables.add(new SpatialTable(owner, table, geomCol, "SDO_GEOMETRY", 4326, rowCount, null));
        }
      }

    } catch (SQLException e) {
      log.error("Oracle introspection failed: {}", e.getMessage(), e);
      throw new RuntimeException("Introspection failed: " + e.getMessage(), e);
    }

    log.info("Oracle introspection: discovered {} spatial tables", tables.size());
    return new IntrospectionResult(tables);
  }

  private static long fetchRowCount(Connection c, String owner, String table) {
    String sql =
        "SELECT num_rows FROM all_tables WHERE owner = '"
            + owner.replace("'", "''")
            + "' AND table_name = '"
            + table.replace("'", "''")
            + "'";
    try (Statement st = c.createStatement();
        ResultSet rs = st.executeQuery(sql)) {
      if (rs.next()) {
        long count = rs.getLong(1);
        return rs.wasNull() ? -1 : count;
      }
      return -1;
    } catch (SQLException e) {
      log.warn("Row count failed for {}.{}: {}", owner, table, e.getMessage());
      return -1;
    }
  }
}
