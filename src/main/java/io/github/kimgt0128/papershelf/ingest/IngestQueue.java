package io.github.kimgt0128.papershelf.ingest;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IngestQueue {
    public record Claim(UUID jobId, String shardId, UUID token) {}
    private final JdbcClient jdbc;

    public IngestQueue(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Claim> claim() {
        return jdbc.sql("""
                UPDATE ingest_jobs SET status='PROCESSING', token=?, lease_until=now()+interval '5 minutes'
                WHERE job_id=(SELECT job_id FROM ingest_jobs WHERE status='QUEUED'
                    OR (status='PROCESSING' AND lease_until <= now())
                    ORDER BY created_at, job_id LIMIT 1 FOR UPDATE SKIP LOCKED)
                RETURNING job_id, shard_id, token
                """).param(UUID.randomUUID()).query(Claim.class).optional();
    }

    public boolean renew(Claim c) {
        return jdbc.sql("UPDATE ingest_jobs SET lease_until=now()+interval '5 minutes' WHERE job_id=? AND token=?")
                .params(c.jobId(), c.token()).update() == 1;
    }

    public void finish(Claim c, String failedStage) {
        jdbc.sql("""
                UPDATE ingest_jobs SET status=:status, failed_stage=:stage, token=NULL, lease_until=NULL
                WHERE job_id=:id AND token=:token
                """).param("status", failedStage == null ? "COMPLETED" : "FAILED")
                .param("stage", failedStage, java.sql.Types.VARCHAR)
                .param("id", c.jobId()).param("token", c.token()).update();
    }
}
