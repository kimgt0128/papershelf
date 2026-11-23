package io.github.kimgt0128.papershelf.index;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

@Configuration
public class MiniLmConfiguration {
    @Bean
    HuggingFaceTokenizer tokenizer(@Value("${papershelf.model-dir:models/minilm}") Path dir) throws IOException {
        return HuggingFaceTokenizer.newInstance(dir.resolve("tokenizer.json"),
                Map.of("padding", "false", "truncation", "false"));
    }

    @Bean
    TransformersEmbeddingModel embeddingModel(@Value("${papershelf.model-dir:models/minilm}") Path dir) {
        var model = new TransformersEmbeddingModel();
        model.setDisableCaching(true);
        model.setTokenizerResource(new FileSystemResource(dir.resolve("tokenizer.json")));
        model.setModelResource(new FileSystemResource(dir.resolve("model.onnx")));
        model.setTokenizerOptions(Map.of("padding", "longest", "truncation", "true", "maxLength", "256"));
        return model;
    }
}
