package com.animelist.backend.dto.anilist;

import java.util.List;

public record StaffDetailResponse(
        Integer anilistId,
        String name,
        String nativeName,
        String image,
        String description,
        String language,
        List<String> occupations,
        String gender,
        Integer age,
        String birthday,
        String homeTown,
        Integer favourites,
        List<StaffCharacterResponse> characters,
        List<PersonMediaResponse> works
) {
}
