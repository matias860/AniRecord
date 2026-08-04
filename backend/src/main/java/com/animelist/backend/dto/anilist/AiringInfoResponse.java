package com.animelist.backend.dto.anilist;

public record AiringInfoResponse(
        Integer episode,
        Long airingAt,
        Long timeUntilAiring
) {
}
