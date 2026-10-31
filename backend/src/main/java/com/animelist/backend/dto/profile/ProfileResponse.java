package com.animelist.backend.dto.profile;

public record ProfileResponse(
        String displayName, String bio, String avatarUrl, String bannerUrl,
        String backgroundColor, String surfaceColor, String accentColor, Boolean dotsEnabled,
        String adultContentMode, String titleLanguage,
        String accountType, String anilistAvatarUrl, String anilistBannerUrl,
        boolean customAvatar, boolean customBanner
) {
}
