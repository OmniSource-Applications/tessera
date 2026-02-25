package live.omnisource.tessera.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_sources", schema = "tessera")
public class ExternalSource {

    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Setter
    @Getter
    @Column(name = "name", unique = true, nullable = false)
    private String name;

    @Getter
    @Setter
    @Column(name = "source_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private SourceType sourceType;

    @Getter
    @Setter
    @Column(name = "connection_key", nullable = false)
    private String connectionKey;

    @Getter
    @Setter
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Getter
    @Setter
    @Column(name = "geometry_column")
    private String geometryColumn;

    @Column(name = "geo_field")
    private String geoField;

    @Getter
    @Setter
    @Column(name = "sync_strategy", nullable = false)
    @Enumerated(EnumType.STRING)
    private SyncStrategy syncStrategy = SyncStrategy.POLL;

    @Setter
    @Getter
    @Column(name = "last_introspect")
    private Instant lastIntrospect;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum SourceType { POSTGIS, MYSQL, ORACLE, CASSANDRA, ELASTICSEARCH }

    public enum SyncStrategy { POLL, CDC, BATCH }

    @PrePersist
    public void onInsert() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
}