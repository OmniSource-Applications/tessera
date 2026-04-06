package live.omnisource.tessera.integration.web;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import live.omnisource.tessera.support.IntegrationTestBase;

@AutoConfigureMockMvc
class ApiControllersIT extends IntegrationTestBase {

  @Autowired MockMvc mvc;

  // ── Query API ──────────────────────────────────

  @Test
  @WithMockUser(username = "admin")
  void queryApi_listReturnsSeededEntries() throws Exception {
    mvc.perform(get("/api/queries"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(7))))
        .andExpect(jsonPath("$[0].name", notNullValue()));
  }

  @Test
  @WithMockUser(username = "admin")
  void queryApi_listFiltersByCategory() throws Exception {
    mvc.perform(get("/api/queries").param("category", "H3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].category", everyItem(is("H3"))));
  }

  @Test
  @WithMockUser(username = "admin")
  void queryApi_getByName() throws Exception {
    mvc.perform(get("/api/queries/features.by_bbox"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("features.by_bbox"))
        .andExpect(jsonPath("$.querySql", containsString("ST_Intersects")));
  }

  @Test
  @WithMockUser(username = "admin")
  void queryApi_getByName_notFound() throws Exception {
    mvc.perform(get("/api/queries/nonexistent")).andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(username = "admin")
  void queryApi_executeSimpleQuery() throws Exception {
    mvc.perform(get("/api/queries/sources.sync_status/execute"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.rows").isArray());
  }

  @Test
  @WithMockUser(username = "admin")
  void queryApi_createAndDeleteEntry() throws Exception {
    String name = "test.api_crud_" + System.nanoTime();

    String body =
        """
            {"name":"%s","category":"CUSTOM","querySql":"SELECT 1","timeoutMs":5000,"tags":["test"]}
            """
            .formatted(name);

    var createResult =
        mvc.perform(
                post("/api/queries")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andReturn();

    String id =
        com.jayway.jsonpath.JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    mvc.perform(delete("/api/queries/" + id).with(csrf())).andExpect(status().isNoContent());

    mvc.perform(get("/api/queries/" + name)).andExpect(status().isNotFound());
  }

  // ── Builder API ────────────────────────────────

  @Test
  @WithMockUser(username = "admin")
  void builderApi_tablesReturnsMetadata() throws Exception {
    mvc.perform(get("/api/builder/tables"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
        .andExpect(jsonPath("$[?(@.table=='geo_features')]").exists())
        .andExpect(jsonPath("$[?(@.table=='geo_features')].columns", hasSize(greaterThan(0))));
  }

  @Test
  @WithMockUser(username = "admin")
  void builderApi_tableColumns() throws Exception {
    mvc.perform(get("/api/builder/tables/geo_features/columns"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(greaterThan(5))))
        .andExpect(jsonPath("$[?(@.name=='geometry')].isGeometry").value(hasItem(true)));
  }

  @Test
  @WithMockUser(username = "admin")
  void builderApi_rejectsUnknownTable() throws Exception {
    mvc.perform(get("/api/builder/tables/not_a_table/columns"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", containsString("Unknown table")));
  }

  // ── Auth enforcement ───────────────────────────

  @Test
  void apiEndpoints_require_authentication() throws Exception {
    mvc.perform(get("/api/queries")).andExpect(status().isUnauthorized());
  }
}
