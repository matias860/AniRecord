package com.animelist.backend.dto.anilist;

import java.util.List;

public record MediaCharacterResponse(
        Integer anilistId,
        String name,
        String image,
        String role,
        List<VoiceActorResponse> voiceActors
) {
}
