package com.animelist.backend.dto.anilist;

import java.util.List;

public record GlobalSearchResponse(
        List<GlobalSearchItemResponse> media,
        List<GlobalSearchItemResponse> characters,
        List<GlobalSearchItemResponse> staff
) {
}
