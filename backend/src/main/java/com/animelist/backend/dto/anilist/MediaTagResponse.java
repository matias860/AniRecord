package com.animelist.backend.dto.anilist;

public record MediaTagResponse(
        String name,
        Integer rank,
        String description,
        String category
) {
}
