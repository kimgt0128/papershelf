package io.github.kimgt0128.papershelf.search;

import jakarta.validation.constraints.*;

public record SearchRequest(@NotBlank @Size(max = 2000) String question,
        @Pattern(regexp = "[0-9]{4}\\.[0-9]{4,5}") String paperId,
        @Positive Integer version, Mode mode) {
    public enum Mode { VECTOR, HYBRID }

    @AssertTrue(message = "paperId and version must be supplied together")
    public boolean isScopeValid() {
        return (paperId == null) == (version == null);
    }
}
