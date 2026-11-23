package io.github.kimgt0128.papershelf.index;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SectionAwareChunkerTest {
    @Test
    void longParagraphRetainsEveryCharacterAndFitsTheActualTokenizer() throws Exception {
        try (var tokenizer = new MiniLmConfiguration().tokenizer(Path.of("models/minilm"))) {
            var text = "MPI_Allreduce measurements on 32 nodes. ".repeat(150) + "Last trial.";
            var chunks = new SectionAwareChunker(tokenizer)
                    .split(List.of(new PaperParagraph(47, "Evaluation", text, "source#47")));
            assertThat(chunks.size()).isGreaterThan(1);
            var restored = new StringBuilder();
            for (var chunk : chunks) {
                assertThat(chunk.paragraphId()).isEqualTo(47);
                assertThat(chunk.startOffset()).isEqualTo(restored.length());
                assertThat(tokenizer.encode(chunk.text(), true, false).getIds().length).isLessThanOrEqualTo(256);
                restored.append(chunk.text());
            }
            assertThat(restored.toString()).isEqualTo(text);
        }
    }
}
