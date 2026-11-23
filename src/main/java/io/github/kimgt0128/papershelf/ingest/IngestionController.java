package io.github.kimgt0128.papershelf.ingest;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
public class IngestionController {
    private final IngestJobStore jobs;

    public IngestionController(IngestJobStore jobs) {
        this.jobs = jobs;
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestJob> submit(@Valid @RequestBody SubmitIngestRequest request) {
        IngestJob job = jobs.submit(request);
        return ResponseEntity.accepted().location(URI.create("/jobs/" + job.jobId())).body(job);
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<IngestJob> find(@PathVariable UUID jobId) {
        return ResponseEntity.ok(jobs.find(jobId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Job not found")));
    }
}
