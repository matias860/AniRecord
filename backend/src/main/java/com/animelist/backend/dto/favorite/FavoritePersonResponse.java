package com.animelist.backend.dto.favorite;

import com.animelist.backend.enums.PersonType;

public record FavoritePersonResponse(
        Long id, Integer anilistId, PersonType personType, String name, String nativeName, String imageUrl
) {}
