package com.animelist.backend.dto.anilist;

import com.animelist.backend.enums.MediaType;

public record MediaRelationResponse(
        Integer anilistId,
        String relationType,
        MediaType mediaType,
        String format,
        String title,
        String coverImage
) {
}
