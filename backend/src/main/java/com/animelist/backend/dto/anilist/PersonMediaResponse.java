package com.animelist.backend.dto.anilist;

import com.animelist.backend.enums.MediaType;

public record PersonMediaResponse(
        Integer anilistId,
        MediaType mediaType,
        String title,
        String coverImage,
        String role
) {
}
