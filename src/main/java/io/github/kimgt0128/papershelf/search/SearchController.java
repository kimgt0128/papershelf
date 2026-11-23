package io.github.kimgt0128.papershelf.search;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class SearchController {
    private final PaperPassageRetriever retriever;

    public SearchController(PaperPassageRetriever retriever) {
        this.retriever = retriever;
    }

    @PostMapping("/search")
    public ResponseEntity<PaperPassageRetriever.Result> search(@Valid @RequestBody SearchRequest request) {
        return ResponseEntity.ok(retriever.search(request));
    }
}
