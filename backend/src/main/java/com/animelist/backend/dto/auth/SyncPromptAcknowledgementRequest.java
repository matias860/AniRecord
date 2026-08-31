package com.animelist.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SyncPromptAcknowledgementRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "[A-Za-z0-9._-]+")
        String commit
) {
}
