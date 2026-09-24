package com.animelist.backend.dto.topnine;

import com.animelist.backend.enums.MediaType;

public record TopNineMediaResponse(
        Long libraryEntryId,
        Integer position,
        Integer anilistId,
        MediaType mediaType,
        String title,
        String romajiTitle,
        String coverImage
) {
}
