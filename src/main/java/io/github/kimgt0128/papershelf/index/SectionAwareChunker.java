package io.github.kimgt0128.papershelf.index;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import io.github.kimgt0128.papershelf.logging.LogStage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SectionAwareChunker {
    public record Chunk(int paragraphId, int startOffset, String text) {}
    private final HuggingFaceTokenizer tokenizer;

    public SectionAwareChunker(HuggingFaceTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    @LogStage("chunk")
    public List<Chunk> split(List<PaperParagraph> paragraphs) {
        var chunks = new ArrayList<Chunk>();
        for (var paragraph : paragraphs) {
            split(paragraph.paragraphId(), 0, paragraph.text(), chunks);
        }
        return List.copyOf(chunks);
    }

    private void split(int id, int offset, String text, List<Chunk> chunks) {
        if (text.isBlank()) return;
        if (tokenizer.encode(text, true, false).getIds().length <= 256) {
            chunks.add(new Chunk(id, offset, text));
            return;
        }
        int middle = text.length() / 2;
        int boundary = middle;
        while (boundary < text.length() && !Character.isWhitespace(text.charAt(boundary))) boundary++;
        if (boundary == text.length()) {
            boundary = middle;
            if (Character.isLowSurrogate(text.charAt(boundary))) boundary++;
        }
        split(id, offset, text.substring(0, boundary), chunks);
        split(id, offset + boundary, text.substring(boundary), chunks);
    }
}
