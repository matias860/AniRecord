package com.animelist.backend.dto.anilist;

public record MediaCharacterResponse(
        Integer anilistId,
        String name,
        String image,
        String role
) {
}
