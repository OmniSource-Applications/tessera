package live.omnisource.tessera.sync;

import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKTReader;
import org.postgresql.util.PGobject;

/**
 * Converts raw geometry values from external JDBC result sets into JTS Geometry.
 *
 * Handles:
 *   - PGobject (PostGIS geometry/geography) → WKB hex → JTS
 *   - byte[] (raw WKB) → JTS
 *   - String (WKT or WKB hex) → JTS
 *   - Lat/lng double pair → JTS Point
 */
@Slf4j
public final class GeometryConverter {

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    private static final WKBReader WKB_READER = new WKBReader(GEOMETRY_FACTORY);
    private static final WKTReader WKT_READER = new WKTReader(GEOMETRY_FACTORY);

    private GeometryConverter() {}

    /**
     * Convert a single geometry column value to JTS Geometry.
     */
    public static Geometry convert(Object raw) {
        if (raw == null) return null;

        try {
            // PostGIS PGobject → WKB hex string
            if (raw instanceof PGobject pg) {
                return fromWkbHex(pg.getValue());
            }

            // Raw WKB bytes
            if (raw instanceof byte[] bytes) {
                return WKB_READER.read(bytes);
            }

            // String — could be WKB hex or WKT
            if (raw instanceof String s) {
                s = s.trim();
                if (s.isEmpty()) return null;
                // WKB hex is all hex chars, WKT starts with a letter (POINT, LINESTRING, etc.)
                if (looksLikeWkbHex(s)) {
                    return fromWkbHex(s);
                }
                return WKT_READER.read(s);
            }

            log.warn("Unknown geometry type: {} — skipping", raw.getClass().getName());
            return null;

        } catch (ParseException e) {
            log.warn("Failed to parse geometry: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Build a JTS Point from separate lat/lng values (Cassandra pattern).
     */
    public static Geometry fromLatLng(Object lat, Object lng) {
        if (lat == null || lng == null) return null;
        try {
            double latVal = ((Number) lat).doubleValue();
            double lngVal = ((Number) lng).doubleValue();
            if (Double.isNaN(latVal) || Double.isNaN(lngVal)) return null;
            return GEOMETRY_FACTORY.createPoint(new Coordinate(lngVal, latVal));
        } catch (Exception e) {
            log.warn("Failed to create point from lat={}, lng={}: {}", lat, lng, e.getMessage());
            return null;
        }
    }

    public static GeometryFactory factory() {
        return GEOMETRY_FACTORY;
    }

    // ── Internal ──────────────────────────────────────────────

    private static Geometry fromWkbHex(String hex) throws ParseException {
        byte[] bytes = WKBReader.hexToBytes(hex);
        return WKB_READER.read(bytes);
    }

    private static boolean looksLikeWkbHex(String s) {
        // WKB hex starts with 00 or 01 (byte order), WKT starts with a letter
        if (s.length() < 2) return false;
        char first = s.charAt(0);
        return (first == '0' || first == '1') && isHex(s.charAt(1));
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}