package live.omnisource.tessera.model.entity;

import jakarta.persistence.*;
import live.omnisource.tessera.config.JsonbConverter;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Geometry;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Entity
@Table(name = "geo_features", schema = "tessera")
public class GeoFeature {

    // Getters / setters or use Lombok @Data if preferred
    // Using explicit accessors for Java 25 clarity
    @Setter
    @EmbeddedId
    private GeoFeatureId id;

    @Setter
    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Setter
    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Setter
    @Column(name = "source_table", nullable = false)
    private String sourceTable;

    @Column(name = "geometry", nullable = false, columnDefinition = "geometry(Geometry,4326)")
    private Geometry geometry;

    @Column(name = "geometry_type", nullable = false)
    private String geometryType;

    @Setter
    @Column(name = "attributes", columnDefinition = "jsonb", nullable = false)
    @Convert(converter = JsonbConverter.class)
    private Map<String, Object> attributes;

    @Setter
    @Column(name = "data_hash")
    private byte[] dataHash;

    @Column(name = "ingested_at", insertable = false, updatable = false)
    private Instant ingestedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Setter
    @Column(name = "valid_from")
    private Instant validFrom;

    @Setter
    @Column(name = "valid_to")
    private Instant validTo;

    @PrePersist
    void onInsert() {
        ingestedAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
        this.geometryType = geometry == null ? null : geometry.getGeometryType().toUpperCase();
    }

}
