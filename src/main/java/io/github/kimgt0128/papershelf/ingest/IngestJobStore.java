package io.github.kimgt0128.papershelf.ingest;

import java.util.Optional;
import java.util.UUID;

import io.github.kimgt0128.papershelf.logging.LogStage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IngestJobStore {
    private final JdbcClient jdbc;

    public IngestJobStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @LogStage("ingest.submit")
    public IngestJob submit(SubmitIngestRequest request) {
        return jdbc.sql("""
                INSERT INTO ingest_jobs (job_id, run_id, shard_id)
                VALUES (:jobId, :runId, :shardId)
                ON CONFLICT (run_id, shard_id) DO UPDATE SET shard_id = EXCLUDED.shard_id
                RETURNING job_id, run_id, shard_id, status
                """)
                .param("jobId", UUID.randomUUID())
                .param("runId", request.runId())
                .param("shardId", request.shardId())
                .query(IngestJob.class)
                .single();
    }

    public Optional<IngestJob> find(UUID jobId) {
        return jdbc.sql("SELECT job_id, run_id, shard_id, status FROM ingest_jobs WHERE job_id = :jobId")
                .param("jobId", jobId)
                .query(IngestJob.class)
                .optional();
    }
}
