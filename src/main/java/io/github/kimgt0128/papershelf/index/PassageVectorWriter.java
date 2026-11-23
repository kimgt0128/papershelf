package io.github.kimgt0128.papershelf.index;

import io.github.kimgt0128.papershelf.logging.LogStage;
import java.util.Arrays;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class PassageVectorWriter {
    private final SectionAwareChunker chunker;
    private final EmbeddingModel model;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public PassageVectorWriter(SectionAwareChunker chunker, EmbeddingModel model, JdbcTemplate jdbc,
            TransactionTemplate transaction) {
        this.chunker = chunker;
        this.model = model;
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    @LogStage("index")
    public void index(String paper, int version, List<PaperParagraph> paragraphs) {
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM passages WHERE paper_id=? AND version=?)",
                Boolean.class, paper, version))) return;
        var chunks = chunker.split(paragraphs);
        if (chunks.isEmpty()) throw new IllegalArgumentException("No passages to index");
        var rows = new java.util.ArrayList<Object[]>(chunks.size());
        for (int start = 0; start < chunks.size(); start += 16) {
            var batch = chunks.subList(start, Math.min(start + 16, chunks.size()));
            var vectors = model.embed(batch.stream().map(SectionAwareChunker.Chunk::text).toList());
            if (vectors.size() != batch.size()) throw new IllegalStateException("Embedding count mismatch");
            for (int i = 0; i < batch.size(); i++) {
                var chunk = batch.get(i);
                rows.add(new Object[]{paper, version, chunk.paragraphId(), chunk.startOffset(),
                        chunk.text(), Arrays.toString(vectors.get(i))});
            }
        }
        transaction.executeWithoutResult(status -> jdbc.batchUpdate("""
                INSERT INTO passages (paper_id, version, paragraph_id, start_offset, text, embedding)
                VALUES (?, ?, ?, ?, ?, ?::vector) ON CONFLICT DO NOTHING
                """, rows));
    }
}
