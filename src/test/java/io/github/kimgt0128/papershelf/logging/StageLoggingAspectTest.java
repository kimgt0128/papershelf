package io.github.kimgt0128.papershelf.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(StageLoggingAspectTest.ExampleConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class StageLoggingAspectTest {
    @Autowired
    ExampleStage example;

    @Test
    void logsSuccessAndPreservesTheReturnValue(CapturedOutput output) {
        assertThat(example.read("private input")).isEqualTo("private input");
        assertThat(output.getAll()).contains("\"message\":\"stage.completed\"", "\"stage\":\"read\"", "\"elapsed_ms\":")
                .doesNotContain("private input");
    }

    @Test
    void logsFailureAndRethrowsTheOriginalException(CapturedOutput output) {
        var failure = new IllegalStateException("private error detail");
        assertThatThrownBy(() -> example.fail(failure)).isSameAs(failure);
        assertThat(output.getAll()).contains("\"message\":\"stage.failed\"", "\"stage\":\"parse\"",
                "\"error_type\":\"java.lang.IllegalStateException\"").doesNotContain("private error detail");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExampleConfiguration {
        @Bean
        ExampleStage exampleStage() {
            return new ExampleStage();
        }
    }

    static class ExampleStage {
        @LogStage("read")
        public String read(String input) {
            return input;
        }

        @LogStage("parse")
        public void fail(RuntimeException failure) {
            throw failure;
        }
    }
}
