package com.animelist.backend.dto.anilist;

public record GlobalSearchItemResponse(
        Integer anilistId,
        String kind,
        String title,
        String subtitle,
        String image,
        String englishTitle,
        String romajiTitle,
        String nativeTitle,
        Boolean adultContent
) {
    public GlobalSearchItemResponse(Integer anilistId, String kind, String title, String subtitle, String image) {
        this(anilistId, kind, title, subtitle, image, null, null, null, false);
    }
}
