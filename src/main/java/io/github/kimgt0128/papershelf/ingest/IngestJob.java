package io.github.kimgt0128.papershelf.ingest;

import java.util.UUID;

public record IngestJob(UUID jobId, UUID runId, String shardId, String status) {}
