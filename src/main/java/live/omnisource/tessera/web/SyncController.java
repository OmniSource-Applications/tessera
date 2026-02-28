package live.omnisource.tessera.web;

import live.omnisource.tessera.sync.AsyncSyncRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Triggers layer sync operations from the UI.
 *
 * Sync runs asynchronously — the request returns immediately
 * with a flash message while ingestion proceeds in the background.
 */
@Slf4j
@Controller
@RequestMapping("/workspaces/{workspace}/datastores/{datastore}/layers/{layer}/sync")
public class SyncController {

    private final AsyncSyncRunner asyncRunner;

    public SyncController(AsyncSyncRunner asyncRunner) {
        this.asyncRunner = asyncRunner;
    }

    @PostMapping
    public String triggerSync(@PathVariable String workspace,
                              @PathVariable String datastore,
                              @PathVariable String layer,
                              RedirectAttributes redirect) {

        log.info("Sync triggered for {}/{}/{}", workspace, datastore, layer);
        asyncRunner.run(workspace, datastore, layer);

        redirect.addFlashAttribute("success",
                "Sync started for layer '" + layer + "'. Features are being ingested in the background.");

        return "redirect:/workspaces/" + workspace + "/datastores/"
                + datastore + "/layers/" + layer;
    }
}
