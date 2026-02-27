package live.omnisource.tessera.datasource.introspection;

import live.omnisource.tessera.layer.dto.IntrospectionResult;
import live.omnisource.tessera.layer.dto.IntrospectionResult.SpatialTable;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * PostGIS-specific spatial table discovery.
 *
 * Queries the geometry_columns view for all user spatial tables,
 * then gathers extent (ST_Extent) and row count for each.
 *
 * Usage from the PostGIS connector:
 *   @Override
 *   public IntrospectionResult introspect(String secretRefKey) {
 *       return PostgisIntrospector.introspect(ds(secretRefKey));
 *   }
 */
@Slf4j
public final class PostgisIntrospector {

    private PostgisIntrospector() {}

    private static final String DISCOVER_SQL = """
            SELECT
                f_table_schema    AS table_schema,
                f_table_name      AS table_name,
                f_geometry_column AS geom_column,
                type              AS geom_type,
                srid
            FROM geometry_columns
            WHERE f_table_schema NOT IN ('pg_catalog', 'information_schema', 'topology', 'partman')
            ORDER BY f_table_schema, f_table_name
            """;

    public static IntrospectionResult introspect(DataSource dataSource) {
        List<SpatialTable> tables = new ArrayList<>();

        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(DISCOVER_SQL)) {

            while (rs.next()) {
                String schema   = rs.getString("table_schema");
                String table    = rs.getString("table_name");
                String geomCol  = rs.getString("geom_column");
                String geomType = rs.getString("geom_type");
                int    srid     = rs.getInt("srid");

                long     rowCount = fetchRowCount(c, schema, table);
                double[] extent   = fetchExtent(c, schema, table, geomCol);

                tables.add(new SpatialTable(schema, table, geomCol, geomType, srid, rowCount, extent));
            }

        } catch (SQLException e) {
            log.error("PostGIS introspection failed: {}", e.getMessage(), e);
            throw new RuntimeException("Introspection failed: " + e.getMessage(), e);
        }

        log.info("PostGIS introspection: discovered {} spatial tables", tables.size());
        return new IntrospectionResult(tables);
    }

    private static long fetchRowCount(Connection c, String schema, String table) {
        String sql = "SELECT count(*) FROM " + quoted(schema, table);
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            log.warn("Row count failed for {}.{}: {}", schema, table, e.getMessage());
            return -1;
        }
    }

    private static double[] fetchExtent(Connection c, String schema, String table, String geomCol) {
        String sql = String.format(
                "SELECT ST_XMin(ext), ST_YMin(ext), ST_XMax(ext), ST_YMax(ext) " +
                        "FROM (SELECT ST_Extent(\"%s\") AS ext FROM %s) sub WHERE ext IS NOT NULL",
                geomCol, quoted(schema, table));
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return new double[]{
                        rs.getDouble(1), rs.getDouble(2),
                        rs.getDouble(3), rs.getDouble(4)
                };
            }
            return null;
        } catch (SQLException e) {
            log.warn("Extent query failed for {}.{}: {}", schema, table, e.getMessage());
            return null;
        }
    }

    private static String quoted(String schema, String table) {
        return "\"" + schema.replace("\"", "\"\"") + "\".\"" + table.replace("\"", "\"\"") + "\"";
    }
}
