package com.animelist.backend.dto.anilist;

import java.util.List;

public record StaffCharacterResponse(
        Integer anilistId,
        String name,
        String image,
        String role,
        List<PersonMediaResponse> media
) {
}
