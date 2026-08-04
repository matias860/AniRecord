package com.animelist.backend.dto.anilist;

import java.util.List;

public record AniListLibraryExport(
        String username,
        String avatarUrl,
        String bannerUrl,
        List<AniListLibraryItem> entries
) {
}
