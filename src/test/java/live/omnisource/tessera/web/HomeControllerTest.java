package live.omnisource.tessera.web;

import live.omnisource.tessera.datastore.DataStoreService;
import live.omnisource.tessera.layer.LayerService;
import live.omnisource.tessera.workspace.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = HomeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(HomeControllerTest.TestConfig.class)
@ActiveProfiles({"dev", "oidc"})
class HomeControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean WorkspaceService workspaceService;
    @MockitoBean DataStoreService dataStoreService;
    @MockitoBean LayerService layerService;

                
    @Test
    void home_rendersLayout_withCounts() throws Exception {
        when(workspaceService.countWorkspaces()).thenReturn(2);
        when(dataStoreService.countAllDataStores()).thenReturn(3);
        when(layerService.countAllLayers()).thenReturn(4);

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("workspacesCount", 2))
                .andExpect(model().attribute("sourcesCount", 3))
                .andExpect(model().attribute("layersCount", 4))
                .andExpect(view().name("layout/page"));
    }

    @Test
    void security_rendersLayout_withProfiles() throws Exception {
        mvc.perform(get("/security"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("activeProfiles"))
                .andExpect(model().attribute("oidcEnabled", true))
                .andExpect(view().name("layout/page"));
    }


    @TestConfiguration
    static class TestConfig {
        @Bean DataStoreService dataStoreService() { return mock(DataStoreService.class); }
        @Bean WorkspaceService workspaceService() { return mock(WorkspaceService.class); }
        @Bean LayerService layerService() { return mock(LayerService.class); }
    }
}
