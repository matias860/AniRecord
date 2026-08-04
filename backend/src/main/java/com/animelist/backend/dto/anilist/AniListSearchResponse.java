package com.animelist.backend.dto.anilist;

import java.util.List;

public record AniListSearchResponse(
        Integer currentPage,
        boolean hasNextPage,
        List<AniListMediaResponse> results
) {
}
