package live.omnisource.tessera.catalog.entity;

import jakarta.persistence.*;
import live.omnisource.tessera.config.JsonbConverter;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A parameterized query stored in the catalog.
 *
 * <p>Queries use named parameters (e.g. {@code :minLon}) and include a
 * JSON Schema describing their expected parameters. The execution engine
 * validates inputs against this schema before running the SQL.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "query_catalog", schema = "tessera")
public class CatalogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(nullable = false)
    private String category;

    @Column(name = "query_sql", nullable = false, columnDefinition = "TEXT")
    private String querySql;

    @Column(name = "param_schema", columnDefinition = "jsonb")
    @Convert(converter = JsonbConverter.class)
    private Map<String, Object> paramSchema;

    @Column(name = "result_schema", columnDefinition = "jsonb")
    @Convert(converter = JsonbConverter.class)
    private Map<String, Object> resultSchema;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs = 30_000;

    @Column(name = "is_streaming", nullable = false)
    private boolean streaming = false;

    @Column(name = "cache_ttl_sec")
    private Integer cacheTtlSec;

    @Column(columnDefinition = "TEXT[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> tags;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        var now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}