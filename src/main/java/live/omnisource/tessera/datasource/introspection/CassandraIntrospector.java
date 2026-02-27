package live.omnisource.tessera.datasource.introspection;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.datastax.oss.driver.api.core.type.DataTypes;
import live.omnisource.tessera.layer.dto.IntrospectionResult;
import live.omnisource.tessera.layer.dto.IntrospectionResult.SpatialTable;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Cassandra-specific spatial table discovery.
 *
 * Cassandra has no native spatial types. This introspector scans
 * keyspace tables for recognizable lat/lng column pairs (double type)
 * and reports them as POINT geometry tables.
 *
 * Recognized latitude column names:  lat, latitude, y, lat_y
 * Recognized longitude column names: lng, lon, longitude, x, lng_x, lon_x
 *
 * Usage from the Cassandra connector:
 *   @Override
 *   public IntrospectionResult introspect(String secretRefKey) {
 *       try (CqlSession session = sessionFor(secretRefKey)) {
 *           return CassandraIntrospector.introspect(session, keyspace);
 *       }
 *   }
 */
@Slf4j
public final class CassandraIntrospector {

    private CassandraIntrospector() {}

    private static final Set<String> LAT_NAMES = Set.of(
            "lat", "latitude", "y", "lat_y"
    );

    private static final Set<String> LNG_NAMES = Set.of(
            "lng", "lon", "longitude", "x", "lng_x", "lon_x"
    );

    /**
     * Introspect the given keyspace for tables with lat/lng column pairs.
     *
     * @param session  an open CqlSession
     * @param keyspace the keyspace to scan
     */
    public static IntrospectionResult introspect(CqlSession session, String keyspace) {
        List<SpatialTable> tables = new ArrayList<>();

        Optional<KeyspaceMetadata> ksMeta = session.getMetadata().getKeyspace(keyspace);
        if (ksMeta.isEmpty()) {
            log.warn("Keyspace '{}' not found in Cassandra metadata", keyspace);
            return new IntrospectionResult(List.of());
        }

        for (TableMetadata table : ksMeta.get().getTables().values()) {
            String tableName = table.getName().asCql(false);

            // Collect all double-type columns
            Map<String, ColumnMetadata> doubleCols = new LinkedHashMap<>();
            for (ColumnMetadata col : table.getColumns().values()) {
                if (col.getType().equals(DataTypes.DOUBLE) || col.getType().equals(DataTypes.FLOAT)) {
                    doubleCols.put(col.getName().asCql(false).toLowerCase(), col);
                }
            }

            // Try to find a lat/lng pair
            String latCol = findMatch(doubleCols.keySet(), LAT_NAMES);
            String lngCol = findMatch(doubleCols.keySet(), LNG_NAMES);

            if (latCol == null || lngCol == null) continue;

            // We found a spatial table
            String geomColumn = latCol + "," + lngCol;
            long rowCount = fetchRowCount(session, keyspace, tableName);
            double[] extent = fetchExtent(session, keyspace, tableName, latCol, lngCol);

            tables.add(new SpatialTable(
                    keyspace,
                    tableName,
                    geomColumn,       // "lat,lng" — both column names
                    "POINT",          // Cassandra lat/lng is always point
                    4326,             // Assumed WGS84
                    rowCount,
                    extent
            ));
        }

        log.info("Cassandra introspection: discovered {} spatial tables in keyspace '{}'",
                tables.size(), keyspace);
        return new IntrospectionResult(tables);
    }

    /**
     * Find the first column name in the set that matches a known spatial name.
     */
    private static String findMatch(Set<String> columnNames, Set<String> knownNames) {
        for (String name : columnNames) {
            if (knownNames.contains(name)) return name;
        }
        // Fuzzy: check if any column name contains a known suffix
        for (String name : columnNames) {
            for (String known : knownNames) {
                if (name.endsWith("_" + known) || name.endsWith("." + known)) {
                    return name;
                }
            }
        }
        return null;
    }

    private static long fetchRowCount(CqlSession session, String keyspace, String table) {
        try {
            ResultSet rs = session.execute(
                    String.format("SELECT count(*) FROM %s.%s", quoted(keyspace), quoted(table)));
            Row row = rs.one();
            return row != null ? row.getLong(0) : -1;
        } catch (Exception e) {
            log.warn("Row count failed for {}.{}: {}", keyspace, table, e.getMessage());
            return -1;
        }
    }

    private static double[] fetchExtent(CqlSession session, String keyspace, String table,
                                        String latCol, String lngCol) {
        try {
            String cql = String.format(
                    "SELECT min(%s), min(%s), max(%s), max(%s) FROM %s.%s",
                    quoted(lngCol), quoted(latCol),
                    quoted(lngCol), quoted(latCol),
                    quoted(keyspace), quoted(table));
            ResultSet rs = session.execute(cql);
            Row row = rs.one();
            if (row != null && !row.isNull(0)) {
                return new double[]{
                        row.getDouble(0),  // minX (lng)
                        row.getDouble(1),  // minY (lat)
                        row.getDouble(2),  // maxX (lng)
                        row.getDouble(3)   // maxY (lat)
                };
            }
            return null;
        } catch (Exception e) {
            log.warn("Extent query failed for {}.{}: {}", keyspace, table, e.getMessage());
            return null;
        }
    }

    private static String quoted(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}