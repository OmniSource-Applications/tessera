package live.omnisource.tessera.stream.adapter.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = RestDeltaController.class)
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(addFilters = false)
@Import(RestDeltaControllerTest.TestConfig.class)
class RestDeltaControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean NamedParameterJdbcTemplate namedJdbc;
    
    @Test
    void poll_clampsLimit_andComputesCursor_andHasMore() throws Exception {
        Instant base = Instant.parse("2026-03-01T00:00:00Z");
        var rows = new ArrayList<Map<String, Object>>();
        // requested limit will be clamped to 1..5000 and query uses +1
        for (int i = 0; i < 6; i++) {
            rows.add(Map.of(
                    "id", i,
                    "updated_at", Timestamp.from(base.plusSeconds(i))
            ));
        }
        when(namedJdbc.queryForList(anyString(), anyMap())).thenReturn(rows);

        mvc.perform(get("/api/stream/poll")
                        .param("since", base.toString())
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.cursor").value(base.plusSeconds(4).toString()));

        verify(namedJdbc).queryForList(contains("ORDER BY f.updated_at ASC"), anyMap());
    }

    @Test
    void poll_includesBboxPredicate_whenAllBboxParamsPresent() throws Exception {
        when(namedJdbc.queryForList(anyString(), anyMap())).thenReturn(List.of());
        Instant base = Instant.parse("2026-03-01T00:00:00Z");

        mvc.perform(get("/api/stream/poll")
                        .param("since", base.toString())
                        .param("minX", "-1")
                        .param("minY", "-1")
                        .param("maxX", "1")
                        .param("maxY", "1"))
                .andExpect(status().isOk());

        verify(namedJdbc).queryForList(contains("ST_MakeEnvelope"), anyMap());
    }


    @TestConfiguration
    static class TestConfig {
        @Bean
        NamedParameterJdbcTemplate namedParameterJdbcTemplate() {
            return mock(NamedParameterJdbcTemplate.class);
        }
    }
}
