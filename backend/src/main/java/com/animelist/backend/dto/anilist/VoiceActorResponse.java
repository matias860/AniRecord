package com.animelist.backend.dto.anilist;

public record VoiceActorResponse(
        Integer anilistId,
        String name,
        String nativeName,
        String image,
        String language
) {
}
