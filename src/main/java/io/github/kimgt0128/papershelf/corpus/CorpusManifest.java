package io.github.kimgt0128.papershelf.corpus;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CorpusManifest {
    private static final Pattern PAPER = Pattern.compile("([0-9]{4}\\.[0-9]{4,5})v([1-9][0-9]{0,5})");
    private final Path root;

    public CorpusManifest(@Value("${papershelf.corpus-root:corpus}") Path root) {
        this.root = root;
    }

    public record PaperRef(String id, int version, Path html, URI source) {}

    public List<PaperRef> papers(String shard) throws IOException {
        if (!shard.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")) {
            throw new IllegalArgumentException("Invalid shard identifier");
        }
        Path directory = root.resolve(shard);
        var papers = Files.readAllLines(directory.resolve("manifest.txt")).stream()
                .map(String::strip).filter(line -> !line.isEmpty()).distinct().map(key -> {
                    var match = PAPER.matcher(key);
                    if (!match.matches()) throw new IllegalArgumentException("Expected a versioned arXiv ID");
                    return new PaperRef(match.group(1), Integer.parseInt(match.group(2)), directory.resolve(key + ".html"),
                            URI.create("https://arxiv.org/html/" + key));
                }).toList();
        if (papers.isEmpty()) throw new IllegalArgumentException("Empty manifest");
        return papers;
    }
}
