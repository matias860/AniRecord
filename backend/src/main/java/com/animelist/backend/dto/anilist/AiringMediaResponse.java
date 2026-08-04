package com.animelist.backend.dto.anilist;

public record AiringMediaResponse(
        Integer anilistId,
        String title,
        String coverImage,
        Integer currentProgress,
        AiringInfoResponse nextAiringEpisode
) {
}
