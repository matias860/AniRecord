package com.animelist.backend.dto.anilist;

import com.animelist.backend.enums.PersonType;

public record AniListPersonFavorite(
        Integer anilistId,
        PersonType personType,
        String name,
        String nativeName,
        String imageUrl,
        Integer favoriteOrder
) {
    public AniListPersonFavorite(Integer id, PersonType type, String name, String nativeName, String imageUrl) {
        this(id, type, name, nativeName, imageUrl, null);
    }
}
