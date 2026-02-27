package live.omnisource.tessera.sync;

import live.omnisource.tessera.sync.dto.SyncJobResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Async wrapper for FeatureSyncService.
 *
 * @Async requires a separate bean (Spring proxies can't intercept self-calls).
 * The sync thread pool is configured via Spring's TaskExecutor.
 */
@Slf4j
@Service
public class AsyncSyncRunner {

    private final FeatureSyncService syncService;

    public AsyncSyncRunner(FeatureSyncService syncService) {
        this.syncService = syncService;
    }

    @Async
    public void run(String workspace, String datastore, String layer) {
        try {
            SyncJobResult result = syncService.syncLayer(workspace, datastore, layer);
            if ("FAILED".equals(result.status())) {
                log.error("Async sync failed for {}/{}/{}: {}",
                        workspace, datastore, layer, result.errorMessage());
            } else {
                log.info("Async sync completed for {}/{}/{}: {} features in {}s",
                        workspace, datastore, layer, result.featuresWritten(),
                        result.duration().toSeconds());
            }
        } catch (Exception e) {
            log.error("Unexpected error in async sync for {}/{}/{}: {}",
                    workspace, datastore, layer, e.getMessage(), e);
        }
    }
}
