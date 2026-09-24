package com.animelist.backend.dto.topnine;

import com.animelist.backend.enums.MediaType;
import java.time.LocalDate;

public record TopNinePromptResponse(
        String key,
        String title,
        String description,
        LocalDate startsAt,
        LocalDate changesAt,
        MediaType mediaType,
        String genre,
        String format
) {
}
