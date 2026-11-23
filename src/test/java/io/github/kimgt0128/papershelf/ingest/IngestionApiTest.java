package io.github.kimgt0128.papershelf.ingest;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IngestionApiTest {
    @Autowired
    MockMvc mvc;

    @Test
    void acceptsAndRetrievesTheSameJobOnRetry() throws Exception {
        UUID runId = UUID.randomUUID();
        String body = """
                {"runId":"%s","shardId":"demo"}
                """.formatted(runId);
        var response = mvc.perform(post("/ingest").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isString())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn().getResponse();
        String location = response.getHeader("Location");
        assertThat(location).startsWith("/jobs/");
        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(content().json(response.getContentAsString()))
                .andExpect(jsonPath("$.runId").value(runId.toString()))
                .andExpect(jsonPath("$.shardId").value("demo"));
        mvc.perform(post("/ingest").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", location));
    }

    @Test
    void rejectsInvalidRequestsAndReportsUnknownJobs() throws Exception {
        mvc.perform(post("/ingest").contentType(APPLICATION_JSON).content("{\"shardId\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/jobs/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON));
    }
}
