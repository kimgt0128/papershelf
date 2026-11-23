package io.github.kimgt0128.papershelf.ingest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "papershelf.worker-enabled=false")
@AutoConfigureMockMvc
@Transactional
class IngestionWorkerTest {
    @TempDir static Path corpus;
    @DynamicPropertySource
    static void corpusRoot(DynamicPropertyRegistry registry) {
        registry.add("papershelf.corpus-root", () -> corpus.toString());
    }

    @Autowired IngestJobStore jobs;
    @Autowired IngestionWorker worker;
    @Autowired IngestQueue queue;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    private IngestJob enqueue(String shard) {
        var job = jobs.submit(new SubmitIngestRequest(UUID.randomUUID(), shard));
        jdbc.update("UPDATE ingest_jobs SET created_at='-infinity' WHERE job_id=?", job.jobId());
        return job;
    }

    @Test
    void ingestsTheManifestAndServesItsVersionedParagraph() throws Exception {
        var shard = Files.createDirectories(corpus.resolve("worker-test"));
        Files.writeString(shard.resolve("manifest.txt"), "1234.56789v1");
        Files.writeString(shard.resolve("1234.56789v1.html"),
                "<article><section id='S1'><h2>Evaluation</h2><p>Ten trials.</p></section></article>");
        var job = enqueue("worker-test");
        worker.runOnce();
        mvc.perform(get("/jobs/" + job.jobId())).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/papers/1234.56789/versions/1/paragraphs/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceUri").value("https://arxiv.org/html/1234.56789v1#S1"))
                .andExpect(jsonPath("$.text").value("Ten trials."));
    }

    @Test
    void retrievesTheRequestedVersionAndItsParent() throws Exception {
        var shard = Files.createDirectories(corpus.resolve("search-test"));
        Files.writeString(shard.resolve("manifest.txt"), "2401.12345v1\n2401.12345v2");
        for (int version = 1; version <= 2; version++) {
            Files.writeString(shard.resolve("2401.12345v" + version + ".html"),
                    "<article><section id='S1'><p>MPI_Allreduce benchmark version " + version + ".</p></section></article>");
        }
        enqueue("search-test");
        worker.runOnce();
        for (String mode : java.util.List.of("VECTOR", "HYBRID")) {
            mvc.perform(post("/search").contentType("application/json").content("""
                    {"question":"MPI_Allreduce","paperId":"2401.12345","version":1,"mode":"%s"}
                    """.formatted(mode))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.hits[0].version").value(1))
                    .andExpect(jsonPath("$.hits[0].paragraph").value("MPI_Allreduce benchmark version 1."))
                    .andExpect(jsonPath("$.candidates[0].sourceUri").value("https://arxiv.org/html/2401.12345v1#S1"));
        }
        mvc.perform(post("/search").contentType("application/json").content("""
                {"question":"MPI_Allreduce","mode":"HYBRID"}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.hits[0].version").value(2));
        mvc.perform(post("/search").contentType("application/json").content("""
                {"question":"MPI_Allreduce","paperId":"2401.12345"}
                """)).andExpect(status().isBadRequest());
    }

    @Test
    void exposesTheFailureStageForAnUnknownShard() {
        var job = enqueue("no-such-shard");
        worker.runOnce();
        var failed = jobs.find(job.jobId()).orElseThrow();
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.failedStage()).isEqualTo("manifest");
    }

    @Test
    void anExpiredClaimCannotOverwriteTheRecoveredJob() {
        var job = enqueue("recovery");
        var old = queue.claim().orElseThrow();
        jdbc.update("UPDATE ingest_jobs SET lease_until='-infinity' WHERE job_id=?", job.jobId());
        var recovered = queue.claim().orElseThrow();
        assertThat(queue.renew(old)).isFalse();
        queue.finish(recovered, null);
        queue.finish(old, "read");
        assertThat(jobs.find(job.jobId()).orElseThrow().status()).isEqualTo("COMPLETED");
    }
}
