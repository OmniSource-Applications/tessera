package live.omnisource.tessera.web;


import live.omnisource.tessera.datasource.connector.DataSourceConnector;
import live.omnisource.tessera.datastore.DataStoreService;
import live.omnisource.tessera.datastore.dto.DataStoreDto;
import live.omnisource.tessera.model.dto.ExternalSourceCredentials;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/workspaces/{workspace}/datastores")
public class DataStoreController {

    private final DataStoreService dataStoreService;

    public DataStoreController(DataStoreService dataStoreService) {
        this.dataStoreService = dataStoreService;
    }

    /**
     * Create a new data store within a workspace.
     */
    @PostMapping
    public String create(@PathVariable String workspace,
                         @RequestParam String name,
                         @RequestParam String type,
                         @RequestParam(required = false) String url,
                         @RequestParam(required = false) String username,
                         @RequestParam(required = false) String password,
                         @RequestParam(required = false, defaultValue = "8") int poolSize,
                         @RequestParam(required = false) String contactPoints,
                         @RequestParam(required = false, defaultValue = "9042") int port,
                         @RequestParam(required = false) String datacenter,
                         @RequestParam(required = false) String keyspace,
                         @RequestParam(required = false) String hosts,
                         @RequestParam(required = false, defaultValue = "true") boolean verifySsl,
                         @RequestParam(required = false) String driverClassName,
                         RedirectAttributes redirect) {
        try {
            var credentials = new ExternalSourceCredentials(
                    type, url, username, password, driverClassName, poolSize,
                    contactPoints, port, datacenter, keyspace, hosts, verifySsl
            );
            var dto = new DataStoreDto(workspace, name);
            DataSourceConnector.ConnectionInfo info = dataStoreService.createDataStore(dto, credentials);

            if (info.connected()) {
                redirect.addFlashAttribute("success",
                        "Data store '" + name + "' created. " + info.version());
            } else {
                redirect.addFlashAttribute("error",
                        "Connection failed: " + info.errorMessage());
            }
        } catch (Exception e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/workspaces/" + workspace;
    }

    /**
     * Delete a data store.
     */
    @PostMapping("/{datastore}/delete")
    public String delete(@PathVariable String workspace,
                         @PathVariable String datastore,
                         RedirectAttributes redirect) {
        try {
            dataStoreService.deleteDataStore(new DataStoreDto(workspace, datastore));
            redirect.addFlashAttribute("success", "Data store '" + datastore + "' deleted.");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/workspaces/" + workspace;
    }

    /**
     * Data store detail / drill-down page.
     */
    @GetMapping("/{datastore}")
    public String detail(@PathVariable String workspace,
                         @PathVariable String datastore,
                         Model model) {
        var record = dataStoreService.getDataStore(new DataStoreDto(workspace, datastore));
        model.addAttribute("title", datastore);
        model.addAttribute("description", "Data store in workspace " + workspace);
        model.addAttribute("view", "datastores/detail");
        model.addAttribute("workspace", workspace);
        model.addAttribute("datastore", record);
        return "layout/page";
    }
}

