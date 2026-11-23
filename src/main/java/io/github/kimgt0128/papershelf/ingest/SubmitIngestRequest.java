package io.github.kimgt0128.papershelf.ingest;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitIngestRequest(@NotNull UUID runId, @NotBlank @Size(max = 128) String shardId) {}
