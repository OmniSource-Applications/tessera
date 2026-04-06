package live.omnisource.tessera.unit.sync;

import live.omnisource.tessera.sync.GeometryConverter;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.WKTWriter;

import static org.assertj.core.api.Assertions.*;

class GeometryConverterTest {

    @Test
    void convert_nullReturnsNull() {
        assertThat(GeometryConverter.convert(null)).isNull();
    }

    @Test
    void convert_wktPointString() {
        var geom = GeometryConverter.convert("POINT (-73.9857 40.7484)");
        assertThat(geom).isNotNull();
        assertThat(geom).isInstanceOf(Point.class);
        assertThat(geom.getSRID()).isEqualTo(4326);
        assertThat(((Point) geom).getX()).isCloseTo(-73.9857, within(0.0001));
        assertThat(((Point) geom).getY()).isCloseTo(40.7484, within(0.0001));
    }

    @Test
    void convert_wktPolygonString() {
        var geom = GeometryConverter.convert("POLYGON ((0 0, 1 0, 1 1, 0 1, 0 0))");
        assertThat(geom).isNotNull();
        assertThat(geom.getGeometryType()).isEqualTo("Polygon");
    }

    @Test
    void convert_wktLineString() {
        var geom = GeometryConverter.convert("LINESTRING (0 0, 1 1, 2 0)");
        assertThat(geom).isNotNull();
        assertThat(geom.getGeometryType()).isEqualTo("LineString");
    }

    @Test
    void convert_emptyStringReturnsNull() {
        assertThat(GeometryConverter.convert("")).isNull();
        assertThat(GeometryConverter.convert("  ")).isNull();
    }

    @Test
    void convert_invalidWktReturnsNull() {
        assertThat(GeometryConverter.convert("NOT A GEOMETRY")).isNull();
    }

    @Test
    void fromLatLng_createsPoint() {
        var geom = GeometryConverter.fromLatLng(40.7484, -73.9857);
        assertThat(geom).isNotNull();
        assertThat(geom).isInstanceOf(Point.class);
        // Note: JTS Point is (x=lng, y=lat)
        assertThat(((Point) geom).getX()).isCloseTo(-73.9857, within(0.0001));
        assertThat(((Point) geom).getY()).isCloseTo(40.7484, within(0.0001));
    }

    @Test
    void fromLatLng_nullLatReturnsNull() {
        assertThat(GeometryConverter.fromLatLng(null, -73.9)).isNull();
    }

    @Test
    void fromLatLng_nullLngReturnsNull() {
        assertThat(GeometryConverter.fromLatLng(40.7, null)).isNull();
    }

    @Test
    void fromLatLng_nanReturnsNull() {
        assertThat(GeometryConverter.fromLatLng(Double.NaN, -73.9)).isNull();
        assertThat(GeometryConverter.fromLatLng(40.7, Double.NaN)).isNull();
    }

    @Test
    void factory_returnsSrid4326() {
        assertThat(GeometryConverter.factory().getSRID()).isEqualTo(4326);
    }

    @Test
    void convert_rawWkbBytes() {
        // Create WKB from a known point, then convert back
        var original = GeometryConverter.convert("POINT (10 20)");
        assertThat(original).isNotNull();

        byte[] wkb = new org.locationtech.jts.io.WKBWriter().write(original);
        var roundTripped = GeometryConverter.convert(wkb);

        assertThat(roundTripped).isNotNull();
        assertThat(roundTripped.equalsExact(original, 0.0001)).isTrue();
    }
}