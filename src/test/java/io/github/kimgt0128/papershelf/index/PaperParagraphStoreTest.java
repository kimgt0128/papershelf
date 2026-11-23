package io.github.kimgt0128.papershelf.index;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class PaperParagraphStoreTest {
    @Autowired PaperParagraphStore store;

    @Test
    void preservesVersionContentOnReplay() {
        var p = new PaperParagraph(1, "Evaluation", "Ten trials", "https://example.org/v1#results");
        store.save("test", 1, "hash1", List.of(p));
        store.save("test", 1, "hash1", List.of(p));
        assertThat(store.find("test", 1, 1)).contains(p);
        assertThat(store.find("test", 2, 1)).isEmpty();
        assertThatThrownBy(() -> store.save("test", 1, "different", List.of(p)))
                .isInstanceOf(IllegalStateException.class);
    }
}
