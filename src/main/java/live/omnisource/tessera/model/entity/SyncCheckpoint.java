package live.omnisource.tessera.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sync_checkpoints", schema = "tessera")
public class SyncCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Getter
    @Setter
    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Getter
    @Setter
    @Column(name = "table_name", nullable = false)
    private String tableName;

    @Getter
    @Setter
    @Column(name = "checkpoint_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private CheckpointType checkpointType;

    @Getter
    @Setter
    @Column(name = "checkpoint_value", nullable = false)
    private String checkpointValue;

    @Getter
    @Column(name = "rows_processed", nullable = false)
    private long rowsProcessed;

    @Getter
    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;

    public enum CheckpointType { LSN, TIMESTAMP, OFFSET, CURSOR }

    public UUID id() { return id; }

    public void addRows(long n) { this.rowsProcessed += n; }

    public void touch() { this.lastSyncedAt = Instant.now(); }
}
