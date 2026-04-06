package live.omnisource.tessera.system.api;

import live.omnisource.tessera.datastore.DataStoreService;
import live.omnisource.tessera.layer.LayerService;
import live.omnisource.tessera.layergroup.LayerGroupService;
import live.omnisource.tessera.security.rbac.TesseraRole;
import live.omnisource.tessera.security.rbac.annotations.RequireGlobalRole;
import live.omnisource.tessera.stream.StreamBroker;
import live.omnisource.tessera.workspace.WorkspaceService;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;

/**
 * System-level REST endpoints for dashboard stats, server info, and health checks.
 */
@RestController
@RequestMapping("/api/system")
public class SystemApiController {

    private final WorkspaceService workspaceService;
    private final DataStoreService dataStoreService;
    private final LayerService layerService;
    private final LayerGroupService layerGroupService;
    private final StreamBroker streamBroker;
    private final Environment environment;

    public SystemApiController(WorkspaceService workspaceService,
                               DataStoreService dataStoreService,
                               LayerService layerService,
                               LayerGroupService layerGroupService,
                               StreamBroker streamBroker,
                               Environment environment) {
        this.workspaceService = workspaceService;
        this.dataStoreService = dataStoreService;
        this.layerService = layerService;
        this.layerGroupService = layerGroupService;
        this.streamBroker = streamBroker;
        this.environment = environment;
    }

    /**
     * Dashboard summary statistics. Available to any authenticated user.
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of(
                "workspaces", workspaceService.countWorkspaces(),
                "datastores", dataStoreService.countAllDataStores(),
                "layers", layerService.countAllLayers(),
                "layerGroups", layerGroupService.countGroups(),
                "activeStreams", streamBroker.subscriberCount()
        );
    }

    /**
     * Server info — profiles, version, runtime details. Admin only.
     */
    @RequireGlobalRole(TesseraRole.TESSERA_SECURITY_ADMIN)
    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "profiles", Arrays.asList(environment.getActiveProfiles()),
                "oidcEnabled", Arrays.asList(environment.getActiveProfiles()).contains("oidc"),
                "java", Map.of(
                        "version", System.getProperty("java.version"),
                        "vendor", System.getProperty("java.vendor"),
                        "virtualThreads", Thread.currentThread().isVirtual()
                ),
                "runtime", Map.of(
                        "processors", Runtime.getRuntime().availableProcessors(),
                        "maxMemoryMb", Runtime.getRuntime().maxMemory() / (1024 * 1024),
                        "freeMemoryMb", Runtime.getRuntime().freeMemory() / (1024 * 1024)
                )
        );
    }
}