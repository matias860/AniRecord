package com.animelist.backend.dto.anilist;

public record MediaRankingResponse(
        Integer rank,
        String type,
        boolean allTime,
        String context,
        Integer year,
        String season
) {
}
