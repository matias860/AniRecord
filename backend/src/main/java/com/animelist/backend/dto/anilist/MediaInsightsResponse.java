package com.animelist.backend.dto.anilist;

import java.util.List;

public record MediaInsightsResponse(
        String trailerUrl,
        String trailerSite,
        Integer durationMinutes,
        Integer averageScore,
        Integer meanScore,
        Integer popularity,
        List<MediaRankingResponse> rankings
) {
}
