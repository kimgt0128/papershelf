ALTER TABLE ingest_jobs
    ADD COLUMN created_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN lease_until timestamptz,
    ADD COLUMN token uuid,
    ADD COLUMN failed_stage text;
