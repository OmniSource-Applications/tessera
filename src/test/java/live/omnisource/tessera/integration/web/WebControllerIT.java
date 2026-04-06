package live.omnisource.tessera.integration.web;

import live.omnisource.tessera.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class WebControllersIT extends IntegrationTestBase {

    @Autowired
    MockMvc mvc;

    // ── Dashboard ──────────────────────────────────

    @Test
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    void dashboard_returnsOk() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("workspacesCount"))
                .andExpect(model().attributeExists("layersCount"));
    }

    // ── Workspaces ─────────────────────────────────

    @Test
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    void workspaces_listReturnsOk() throws Exception {
        mvc.perform(get("/workspaces"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("workspaces"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    void workspaces_createAndAccessWorkspace() throws Exception {
        String name = "testws_" + System.nanoTime();

        // Create
        mvc.perform(post("/workspaces").param("name", name))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/workspaces"));

        // Access detail
        mvc.perform(get("/workspaces/" + name))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("workspace"));

        // Cleanup
        mvc.perform(post("/workspaces/" + name + "/delete"))
                .andExpect(status().is3xxRedirection());
    }

    // ── Query Catalog ──────────────────────────────

    @Test
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    void queryCatalog_indexReturnsOk() throws Exception {
        mvc.perform(get("/query"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("grouped", "totalCount"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    void queryCatalog_detailReturnsOk() throws Exception {
        mvc.perform(get("/query/features.by_bbox"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("entry", "paramNames"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    void queryCatalog_detailRedirectsForMissing() throws Exception {
        mvc.perform(get("/query/nonexistent.query"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/query"));
    }

    // ── Query Builder ──────────────────────────────

    @Test
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    void queryBuilder_pageReturnsOk() throws Exception {
        mvc.perform(get("/query/builder"))
                .andExpect(status().isOk());
    }

    // ── API Keys Page ──────────────────────────────

    @Test
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    void apiKeys_pageReturnsOk() throws Exception {
        mvc.perform(get("/settings/api-keys"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("apiKeysEnabled"));
    }

    // ── Security Page ──────────────────────────────

    @Test
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    void security_pageReturnsOk() throws Exception {
        mvc.perform(get("/security"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("activeProfiles"));
    }

    // ── Authentication ─────────────────────────────

    @Test
    void unauthenticatedRequest_redirectsToLogin() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void loginPage_isPublic() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk());
    }
}