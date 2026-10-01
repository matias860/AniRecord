package com.animelist.backend.dto.anilist;

import java.util.List;

public record CatalogCategoriesResponse(
        List<String> genres,
        List<CatalogTagResponse> tags
) {
}
