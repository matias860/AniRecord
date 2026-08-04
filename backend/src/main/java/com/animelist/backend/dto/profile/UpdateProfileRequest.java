package com.animelist.backend.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 40) String displayName,
        @NotNull @Size(max = 160) String bio,
        @Size(max = 1000) String avatarUrl,
        @Size(max = 1000) String bannerUrl,
        @NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String backgroundColor,
        @NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String surfaceColor,
        @NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String accentColor,
        @NotNull Boolean dotsEnabled
) {
}
