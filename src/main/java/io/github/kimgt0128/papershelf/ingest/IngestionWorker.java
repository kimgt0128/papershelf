package io.github.kimgt0128.papershelf.ingest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;

import io.github.kimgt0128.papershelf.corpus.CorpusManifest;
import io.github.kimgt0128.papershelf.index.PaperHtmlParser;
import io.github.kimgt0128.papershelf.index.PaperParagraphStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IngestionWorker {
    private static final Logger log = LoggerFactory.getLogger(IngestionWorker.class);
    private final IngestQueue queue;
    private final CorpusManifest manifest;
    private final PaperHtmlParser parser;
    private final PaperParagraphStore store;

    public IngestionWorker(IngestQueue queue, CorpusManifest manifest, PaperHtmlParser parser, PaperParagraphStore store) {
        this.queue = queue;
        this.manifest = manifest;
        this.parser = parser;
        this.store = store;
    }

    @Scheduled(fixedDelayString = "${papershelf.poll-ms:1000}")
    public void runOnce() {
        queue.claim().ifPresent(this::process);
    }

    private void process(IngestQueue.Claim claim) {
        String stage = "manifest";
        try {
            for (var paper : manifest.papers(claim.shardId())) {
                if (!queue.renew(claim)) return;
                stage = "read";
                byte[] bytes = Files.readAllBytes(paper.html());
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                stage = "parse";
                var paragraphs = parser.parse(new String(bytes, StandardCharsets.UTF_8), paper.source());
                stage = "store";
                store.save(paper.id(), paper.version(), hash, paragraphs);
            }
        } catch (Exception failure) {
            log.atWarn().addKeyValue("job_id", claim.jobId()).addKeyValue("stage", stage)
                    .addKeyValue("error_type", failure.getClass().getSimpleName()).log("job.failed");
            queue.finish(claim, stage);
            return;
        }
        queue.finish(claim, null);
    }
}
