package live.omnisource.tessera.sync.dto;

import java.time.Duration;
import java.time.Instant;

/**
 * Result of a sync job execution.
 */
public record SyncJobResult(
        String workspace,
        String datastore,
        String layer,
        long featuresRead,
        long featuresWritten,
        long featuresSkipped,
        long h3CellsIndexed,
        Instant startedAt,
        Instant completedAt,
        String status,          // COMPLETED, FAILED, PARTIAL
        String errorMessage
) {
    public Duration duration() {
        return Duration.between(startedAt, completedAt);
    }

    public static SyncJobResult completed(String ws, String ds, String layer,
                                          long read, long written, long skipped,
                                          long h3Cells, Instant start) {
        return new SyncJobResult(ws, ds, layer, read, written, skipped, h3Cells,
                start, Instant.now(), "COMPLETED", null);
    }

    public static SyncJobResult failed(String ws, String ds, String layer,
                                       long read, long written, long skipped,
                                       Instant start, String error) {
        return new SyncJobResult(ws, ds, layer, read, written, skipped, 0,
                start, Instant.now(), "FAILED", error);
    }
}
