package live.omnisource.tessera.sync;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * Converts raw geometry values from external result sets into JTS Geometry.
 *
 * <p>Handles:
 *
 * <ul>
 *   <li>PGobject (PostGIS geometry/geography) → WKB/EWKB hex or EWKT/WKT → JTS
 *   <li>byte[] (raw WKB/EWKB) → JTS
 *   <li>String (WKT, EWKT, WKB hex, EWKB hex) → JTS
 *   <li>Map (Elasticsearch geo_point / GeoJSON-like geo_shape) → JTS
 *   <li>Lat/lng double pair → JTS Point
 * </ul>
 */
@Slf4j
public final class GeometryConverter {

  private static final GeometryFactory GEOMETRY_FACTORY =
          new GeometryFactory(new PrecisionModel(), 4326);

  private static final WKBReader WKB_READER = new WKBReader(GEOMETRY_FACTORY);
  private static final WKTReader WKT_READER = new WKTReader(GEOMETRY_FACTORY);

  private static final int EWKB_SRID_FLAG = 0x20000000;

  private GeometryConverter() {}

  /** Convert a single geometry column value to JTS Geometry. */
  public static Geometry convert(Object raw) {
    if (raw == null) {
      return null;
    }

    try {
      // PostGIS PGobject → WKB/EWKB hex string or EWKT/WKT
      if (raw instanceof PGobject pg) {
        return fromWkbOrEwkbOrText(pg.getValue());
      }

      // Raw WKB/EWKB bytes
      if (raw instanceof byte[] bytes) {
        Geometry geometry = WKB_READER.read(bytes);
        Integer srid = extractEwkbSrid(bytes);
        if (srid != null) {
          geometry.setSRID(srid);
        }
        return geometry;
      }

      // Elasticsearch / JSON object geometry
      if (raw instanceof Map<?, ?> map) {
        return fromMap(map);
      }

      // String — could be EWKT, WKT, WKB hex, or EWKB hex
      if (raw instanceof String s) {
        return fromWkbOrEwkbOrText(s);
      }

      log.warn("Unknown geometry type: {} — skipping", raw.getClass().getName());
      return null;

    } catch (ParseException e) {
      log.warn("Failed to parse geometry: {}", e.getMessage());
      return null;
    }
  }

  /** Build a JTS Point from separate lat/lng values (Cassandra pattern). */
  public static Geometry fromLatLng(Object lat, Object lng) {
    if (lat == null || lng == null) {
      return null;
    }

    try {
      double latVal = ((Number) lat).doubleValue();
      double lngVal = ((Number) lng).doubleValue();
      if (Double.isNaN(latVal) || Double.isNaN(lngVal)) {
        return null;
      }
      return GEOMETRY_FACTORY.createPoint(new Coordinate(lngVal, latVal));
    } catch (Exception e) {
      log.warn("Failed to create point from lat={}, lng={}: {}", lat, lng, e.getMessage());
      return null;
    }
  }

  public static GeometryFactory factory() {
    return GEOMETRY_FACTORY;
  }

  private static Geometry fromWkbOrEwkbOrText(String value) throws ParseException {
    if (value == null) {
      return null;
    }

    String s = value.trim();
    if (s.isEmpty()) {
      return null;
    }

    if (looksLikeEwkt(s)) {
      return fromEwkt(s);
    }

    if (looksLikeWkbHex(s)) {
      return fromWkbOrEwkb(s);
    }

    return WKT_READER.read(s);
  }

  private static Geometry fromWkbOrEwkb(String hex) throws ParseException {
    byte[] bytes = WKBReader.hexToBytes(hex);
    Geometry geometry = WKB_READER.read(bytes);

    Integer srid = extractEwkbSrid(bytes);
    if (srid != null) {
      geometry.setSRID(srid);
    }

    return geometry;
  }

  private static boolean looksLikeEwkt(String s) {
    return s.regionMatches(true, 0, "SRID=", 0, 5);
  }

  private static Geometry fromEwkt(String s) throws ParseException {
    int semicolon = s.indexOf(';');
    if (semicolon < 0) {
      return WKT_READER.read(s);
    }

    String sridPart = s.substring(0, semicolon).trim();
    String wktPart = s.substring(semicolon + 1).trim();

    Geometry geometry = WKT_READER.read(wktPart);

    if (sridPart.regionMatches(true, 0, "SRID=", 0, 5)) {
      try {
        int srid = Integer.parseInt(sridPart.substring(5));
        geometry.setSRID(srid);
      } catch (NumberFormatException e) {
        log.warn("Invalid EWKT SRID '{}'", sridPart);
      }
    }

    return geometry;
  }

  private static boolean looksLikeWkbHex(String s) {
    if (s.length() < 2) {
      return false;
    }
    char first = s.charAt(0);
    return (first == '0' || first == '1') && isHex(s.charAt(1));
  }

  private static boolean isHex(char c) {
    return (c >= '0' && c <= '9')
            || (c >= 'a' && c <= 'f')
            || (c >= 'A' && c <= 'F');
  }

  private static Integer extractEwkbSrid(byte[] bytes) {
    if (bytes == null || bytes.length < 9) {
      return null;
    }

    int byteOrder = bytes[0] & 0xFF;
    boolean littleEndian = byteOrder == 1;

    int typeInt = readInt(bytes, 1, littleEndian);
    if ((typeInt & EWKB_SRID_FLAG) == 0) {
      return null;
    }

    return readInt(bytes, 5, littleEndian);
  }

  private static int readInt(byte[] bytes, int offset, boolean littleEndian) {
    if (littleEndian) {
      return (bytes[offset] & 0xFF)
              | ((bytes[offset + 1] & 0xFF) << 8)
              | ((bytes[offset + 2] & 0xFF) << 16)
              | ((bytes[offset + 3] & 0xFF) << 24);
    }

    return ((bytes[offset] & 0xFF) << 24)
            | ((bytes[offset + 1] & 0xFF) << 16)
            | ((bytes[offset + 2] & 0xFF) << 8)
            | (bytes[offset + 3] & 0xFF);
  }

  private static Geometry fromMap(Map<?, ?> map) throws ParseException {
    if (map.isEmpty()) {
      return null;
    }

    // geo_point format: {lat: ..., lon: ...}
    Object lat = map.get("lat");
    Object lon = map.get("lon");
    if (lat instanceof Number && lon instanceof Number) {
      return GEOMETRY_FACTORY.createPoint(
              new Coordinate(((Number) lon).doubleValue(), ((Number) lat).doubleValue()));
    }

    // GeoJSON-like format: {type: "Point", coordinates: [...]}
    Object typeObj = map.get("type");
    Object coordsObj = map.get("coordinates");

    if (typeObj instanceof String type && coordsObj != null) {
      return fromGeoJsonLike(type, coordsObj);
    }

    log.warn("Unknown map-based geometry structure: {}", map);
    return null;
  }

  private static Geometry fromGeoJsonLike(String type, Object coordinates) throws ParseException {
    return switch (type) {
      case "Point" -> pointFromCoordinates(coordinates);
      case "LineString" -> lineStringFromCoordinates(coordinates);
      case "Polygon" -> polygonFromCoordinates(coordinates);
      default -> {
        log.warn("Unsupported GeoJSON geometry type: {}", type);
        yield null;
      }
    };
  }

  private static Geometry pointFromCoordinates(Object coordinates) {
    if (!(coordinates instanceof List<?> list) || list.size() < 2) {
      return null;
    }

    Object x = list.get(0);
    Object y = list.get(1);
    if (x instanceof Number && y instanceof Number) {
      return GEOMETRY_FACTORY.createPoint(
              new Coordinate(((Number) x).doubleValue(), ((Number) y).doubleValue()));
    }

    return null;
  }

  private static Geometry lineStringFromCoordinates(Object coordinates) {
    if (!(coordinates instanceof List<?> points) || points.isEmpty()) {
      return null;
    }

    Coordinate[] coords =
            points.stream()
                    .map(GeometryConverter::coordinateFromList)
                    .filter(Objects::nonNull)
                    .toArray(Coordinate[]::new);

    if (coords.length < 2) {
      return null;
    }

    return GEOMETRY_FACTORY.createLineString(coords);
  }

  private static Geometry polygonFromCoordinates(Object coordinates) {
    if (!(coordinates instanceof List<?> rings) || rings.isEmpty()) {
      return null;
    }

    Object shellObj = rings.get(0);
    if (!(shellObj instanceof List<?> shellPoints) || shellPoints.isEmpty()) {
      return null;
    }

    Coordinate[] shellCoords =
            shellPoints.stream()
                    .map(GeometryConverter::coordinateFromList)
                    .filter(Objects::nonNull)
                    .toArray(Coordinate[]::new);

    if (shellCoords.length < 4) {
      return null;
    }

    var shell = GEOMETRY_FACTORY.createLinearRing(shellCoords);
    return GEOMETRY_FACTORY.createPolygon(shell);
  }

  private static Coordinate coordinateFromList(Object value) {
    if (!(value instanceof List<?> list) || list.size() < 2) {
      return null;
    }

    Object x = list.get(0);
    Object y = list.get(1);
    if (x instanceof Number && y instanceof Number) {
      return new Coordinate(((Number) x).doubleValue(), ((Number) y).doubleValue());
    }

    return null;
  }
}