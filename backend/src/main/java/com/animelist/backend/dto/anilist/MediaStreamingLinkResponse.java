package com.animelist.backend.dto.anilist;

public record MediaStreamingLinkResponse(
        String site,
        String url,
        String language,
        String color,
        String icon,
        String notes
) {
}
