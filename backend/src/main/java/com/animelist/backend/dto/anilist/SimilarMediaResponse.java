package com.animelist.backend.dto.anilist;

public record SimilarMediaResponse(
        AniListMediaResponse media,
        String reason,
        Integer recommendationVotes
) {
}
