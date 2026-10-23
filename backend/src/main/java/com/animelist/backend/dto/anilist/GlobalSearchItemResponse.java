package com.animelist.backend.dto.anilist;

public record GlobalSearchItemResponse(
        Integer anilistId,
        String kind,
        String title,
        String subtitle,
        String image
) {
}
