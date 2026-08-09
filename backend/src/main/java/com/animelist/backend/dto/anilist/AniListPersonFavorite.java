package com.animelist.backend.dto.anilist;

import com.animelist.backend.enums.PersonType;

public record AniListPersonFavorite(
        Integer anilistId,
        PersonType personType,
        String name,
        String nativeName,
        String imageUrl
) {
}
