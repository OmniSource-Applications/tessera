package live.omnisource.tessera.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Setter
@Getter
@Embeddable
public class GeoFeatureId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "id")
    private Long id;

    @Column(name = "ingested_at")
    private Instant ingestedAt;

    public GeoFeatureId() {}

    public GeoFeatureId(Long id, Instant ingestedAt) {
        this.id = id;
        this.ingestedAt = ingestedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeoFeatureId that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(ingestedAt, that.ingestedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, ingestedAt);
    }

    @Override
    public String toString() {
        return "GeoFeatureId{id=" + id + ", ingestedAt=" + ingestedAt + '}';
    }
}
