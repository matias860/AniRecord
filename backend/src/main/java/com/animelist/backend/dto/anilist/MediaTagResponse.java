package com.animelist.backend.dto.anilist;

public record MediaTagResponse(
        String name,
        Integer rank,
        String description,
        String category,
        boolean spoiler
) {
    public MediaTagResponse(String name, Integer rank, String description, String category) {
        this(name, rank, description, category, false);
    }
}
