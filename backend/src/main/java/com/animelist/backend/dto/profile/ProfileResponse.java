package com.animelist.backend.dto.profile;

public record ProfileResponse(
        String displayName, String bio, String avatarUrl, String bannerUrl,
        String backgroundColor, String surfaceColor, String accentColor, Boolean dotsEnabled
) {
}
