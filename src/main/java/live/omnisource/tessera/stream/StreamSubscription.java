package live.omnisource.tessera.stream;

import org.locationtech.jts.geom.Envelope;

import java.time.Instant;
import java.util.UUID;

public class StreamSubscription {
    private final String id;
    private final Protocol protocol;
    private final UUID sourceId;
    private final String sourceTable;
    private final Envelope spatialFilter;
    private volatile Instant cursor;
    private volatile Instant lastDeliveredAt;
    private long deliveredCount;
    private final Instant createdAt;
    private volatile boolean active = true;

    public enum Protocol {
        SSE,
        WEBSOCKET,
        REST_POLL
    }

    public StreamSubscription(
            String id,
            Protocol protocol,
            UUID sourceId,
            String sourceTable,
            Envelope spatialFilter,
            Instant cursor) {
        this.id = id;
        this.protocol = protocol;
        this.sourceId = sourceId;
        this.sourceTable = sourceTable;
        this.spatialFilter = spatialFilter;
        this.cursor = cursor != null ? cursor : Instant.now();
        this.createdAt = Instant.now();
    }

    public boolean matches(UUID eventSourceId, String eventSourceTable, Envelope eventEnvelope) {
        if (!active) {
            return false;
        }

        if (sourceId != null && !sourceId.equals(eventSourceId)) {
            return false;
        }

        if (sourceTable != null && !sourceTable.equals(eventSourceTable)) {
            return false;
        }

        return spatialFilter == null || eventEnvelope == null || spatialFilter.intersects(eventEnvelope);
    }

    public void advanceCursor(Instant to) {
        this.cursor = to;
        this.lastDeliveredAt = Instant.now();
    }

    public void incrementDelivered(long n) {
        this.deliveredCount += n;
    }

    public void deactivate() {
        this.active = false;
    }

    public String id()              { return id; }
    public Protocol protocol()      { return protocol; }
    public UUID sourceId()          { return sourceId; }
    public String sourceTable()     { return sourceTable; }
    public Envelope spatialFilter() { return spatialFilter; }
    public Instant cursor()         { return cursor; }
    public Instant lastDeliveredAt(){ return lastDeliveredAt; }
    public long deliveredCount()    { return deliveredCount; }
    public Instant createdAt()      { return createdAt; }
    public boolean isActive()       { return active; }
}
