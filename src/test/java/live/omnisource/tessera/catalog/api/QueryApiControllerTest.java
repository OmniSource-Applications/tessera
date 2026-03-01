package live.omnisource.tessera.catalog.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import live.omnisource.tessera.catalog.QueryCatalogService;
import live.omnisource.tessera.catalog.QueryExecutor;
import live.omnisource.tessera.catalog.entity.CatalogEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = QueryApiController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(QueryApiControllerTest.TestConfig.class)
class QueryApiControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockitoBean QueryCatalogService catalogService;
    @MockitoBean QueryExecutor queryExecutor;


    @Test
    void list_returnsCatalogEntries() throws Exception {
        var e = new CatalogEntry();
        e.setName("features.by_bbox");
        e.setDescription("d");
        e.setCategory("STATIC");
        when(catalogService.listAll()).thenReturn(List.of(e));

        mvc.perform(get("/api/queries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("features.by_bbox"));
    }

    @Test
    void getByName_404_whenMissing() throws Exception {
        when(catalogService.getByName("nope")).thenReturn(null);

        mvc.perform(get("/api/queries/nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    void execute_runsQueryExecutor_andReturnsResult() throws Exception {
        var e = new CatalogEntry();
        e.setName("features.by_bbox");
        when(catalogService.getByName("features.by_bbox")).thenReturn(e);

        when(queryExecutor.execute(eq(e), anyMap())).thenReturn(
                QueryExecutor.QueryResult.success("features.by_bbox", List.of(Map.of("id", 1)), Duration.ofMillis(5), false)
        );

        mvc.perform(post("/api/queries/features.by_bbox/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("minLon", 0, "minLat", 0, "maxLon", 1, "maxLat", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.rowCount").value(1));
    }


    @TestConfiguration
    static class TestConfig {
        @Bean
        QueryCatalogService catalogService() {
            return mock(QueryCatalogService.class);
        }

        @Bean
        QueryExecutor executor() {
            return mock(QueryExecutor.class);
        }
    }
}
