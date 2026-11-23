package io.github.kimgt0128.papershelf.index;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class PaperParagraphController {
    private final PaperParagraphStore store;

    public PaperParagraphController(PaperParagraphStore store) {
        this.store = store;
    }

    @GetMapping("/papers/{id}/versions/{version}/paragraphs/{paragraphId}")
    public ResponseEntity<PaperParagraph> get(@PathVariable String id, @PathVariable int version,
            @PathVariable int paragraphId) {
        return ResponseEntity.of(store.find(id, version, paragraphId));
    }
}
