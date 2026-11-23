package io.github.kimgt0128.papershelf.search;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import io.github.kimgt0128.papershelf.logging.LogStage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaperPassageRetriever {
    public record Hit(String paperId, int version, int paragraphId, int startOffset,
            String text, String paragraph, String sourceUri, double score) {}
    public record Result(List<Hit> hits, List<Hit> candidates, double elapsedMs) {}

    private static final Pattern TERM = Pattern.compile("[\\p{L}\\p{N}_]+");
    private final String sql;
    private final JdbcClient jdbc;
    private final EmbeddingModel model;
    private final HuggingFaceTokenizer tokenizer;

    public PaperPassageRetriever(JdbcClient jdbc, EmbeddingModel model, HuggingFaceTokenizer tokenizer)
            throws IOException {
        this.jdbc = jdbc;
        this.model = model;
        this.tokenizer = tokenizer;
        this.sql = new ClassPathResource("search/passages.sql").getContentAsString(StandardCharsets.UTF_8);
    }

    @LogStage("retrieve")
    public Result search(SearchRequest request) {
        long started = System.nanoTime();
        if (tokenizer.encode(request.question(), true, false).getIds().length > 256) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question exceeds 256 model tokens");
        }
        if (request.paperId() != null && !jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM passages WHERE paper_id=? AND version=?)
                """).params(request.paperId(), request.version()).query(Boolean.class).single()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Paper version is not indexed");
        }
        String terms = TERM.matcher(request.question().toLowerCase(Locale.ROOT)).results()
                .map(match -> "'" + match.group() + "'").distinct().collect(Collectors.joining(" | "));
        var candidates = jdbc.sql(sql)
                .param("paper", request.paperId(), Types.VARCHAR)
                .param("version", request.version(), Types.INTEGER)
                .param("hybrid", request.mode() == SearchRequest.Mode.HYBRID && !terms.isEmpty())
                .param("terms", terms)
                .param("embedding", Arrays.toString(model.embed(request.question())))
                .query(Hit.class).list();
        return new Result(candidates.stream().limit(5).toList(), candidates,
                (System.nanoTime() - started) / 1_000_000.0);
    }
}
