package io.github.kimgt0128.papershelf.index;

import java.net.URI;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaperHtmlParserTest {
    @Test
    void attachesTheNearestSectionAndAnchorInReadingOrder() {
        var uri = URI.create("https://arxiv.org/html/2508.13397v1");
        var html = """
                <article><nav><p>Navigation</p></nav><section><h2>Experiments</h2>
                <p>Overview.</p><section id="S4.SS1"><h3>4.1 MPI Analysis</h3>
                <div id="S4.SS1.p1"><p>We repeated the experiment
                <math><semantics><mn>10</mn><annotation encoding="application/x-tex">10</annotation></semantics></math> times.</p></div>
                </section></section><section class="ltx_bibliography"><p>Reference</p></section></article>
                """;
        assertThat(new PaperHtmlParser().parse(html, uri)).containsExactly(
                new PaperParagraph(1, "Experiments", "Overview.", uri.toString()),
                new PaperParagraph(2, "4.1 MPI Analysis", "We repeated the experiment 10 times.", uri + "#S4.SS1.p1"));
    }
}
