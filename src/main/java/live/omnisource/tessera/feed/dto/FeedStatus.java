package live.omnisource.tessera.feed.dto;

import java.time.Instant;
import live.omnisource.tessera.feed.dto.FeedConfig.FeedType;
import live.omnisource.tessera.feed.dto.FeedConfig;

/**
 * Runtime status of a feed — not persisted, computed from the running state.
 */
public record FeedStatus(
        String name,
        FeedType type,
        State state,
        long messagesReceived,
        long featuresIngested,
        long errors,
        Instant startedAt,
        Instant lastMessageAt,
        String lastError
) {
    public enum State {
        STOPPED,
        STARTING,
        RUNNING,
        ERROR,
        RECONNECTING
    }

    public static FeedStatus stopped(FeedConfig config) {
        return new FeedStatus(
                config.name(), config.type(), State.STOPPED,
                0, 0, 0, null, null, null
        );
    }
}