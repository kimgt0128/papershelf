package io.github.kimgt0128.papershelf.index;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PaperParagraphStore {
    private final JdbcTemplate jdbc;
    private final JdbcClient query;

    public PaperParagraphStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.query = JdbcClient.create(jdbc);
    }

    @Transactional
    public void save(String id, int version, String hash, List<PaperParagraph> paragraphs) {
        int inserted = jdbc.update("INSERT INTO paper_versions VALUES (?, ?, ?) ON CONFLICT DO NOTHING", id, version, hash);
        if (inserted == 0) {
            String existing = jdbc.queryForObject("SELECT html_sha256 FROM paper_versions WHERE paper_id=? AND version=?",
                    String.class, id, version);
            if (!hash.equals(existing)) throw new IllegalStateException("Paper version content changed");
            return;
        }
        jdbc.batchUpdate("INSERT INTO paper_paragraphs VALUES (?, ?, ?, ?, ?, ?)", paragraphs, 128, (ps, p) -> {
            ps.setString(1, id);
            ps.setInt(2, version);
            ps.setInt(3, p.paragraphId());
            ps.setString(4, p.section());
            ps.setString(5, p.text());
            ps.setString(6, p.sourceUri());
        });
    }

    public Optional<PaperParagraph> find(String id, int version, int paragraphId) {
        return query.sql("""
                SELECT paragraph_id, section, text, source_uri FROM paper_paragraphs
                WHERE paper_id=? AND version=? AND paragraph_id=?
                """).params(id, version, paragraphId).query(PaperParagraph.class).optional();
    }
}
