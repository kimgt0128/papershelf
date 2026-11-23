CREATE TABLE ingest_jobs (
    job_id uuid PRIMARY KEY,
    run_id uuid NOT NULL,
    shard_id varchar(128) NOT NULL,
    status text NOT NULL DEFAULT 'QUEUED',
    UNIQUE (run_id, shard_id)
);
