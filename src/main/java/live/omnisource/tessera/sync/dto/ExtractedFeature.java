package live.omnisource.tessera.sync.dto;

import org.locationtech.jts.geom.Geometry;

import java.util.Map;

/**
 * A feature extracted from an external source, ready for batch insertion.
 *
 * @param externalId  stable ID from the source (PK value, or row hash)
 * @param geometry    JTS geometry, SRID 4326
 * @param attributes  all non-geometry columns as key-value pairs
 * @param dataHash    SHA-256 of the serialized attributes for change detection
 */
public record ExtractedFeature(
        String externalId,
        Geometry geometry,
        Map<String, Object> attributes,
        byte[] dataHash
) {}