package io.github.kimgt0128.papershelf.index;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class PaperHtmlParser {
    public List<PaperParagraph> parse(String html, URI source) {
        var document = Jsoup.parse(html);
        document.select("nav, footer, .ltx_bibliography, math annotation, math annotation-xml").remove();
        var paragraphs = new ArrayList<PaperParagraph>();
        for (Element p : document.select("article p, section p")) {
            String text = p.text();
            if (text.isBlank()) continue;
            var section = p.closest("section");
            var heading = section == null ? null : section.selectFirst(":root > :is(h1,h2,h3,h4,h5,h6)");
            var anchor = p.closest("[id]");
            String uri = anchor == null ? source.toString() : source + "#" + anchor.id();
            paragraphs.add(new PaperParagraph(paragraphs.size() + 1,
                    heading == null ? "" : heading.text(), text, uri));
        }
        if (paragraphs.isEmpty()) throw new IllegalArgumentException("No paper paragraphs found");
        return List.copyOf(paragraphs);
    }
}
