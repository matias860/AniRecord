package com.animelist.backend.dto.anilist;

import java.util.List;

public record CharacterDetailResponse(
        Integer anilistId,
        String name,
        String nativeName,
        String image,
        String description,
        String gender,
        String age,
        String birthday,
        String bloodType,
        Integer favourites,
        List<VoiceActorResponse> voiceActors,
        List<PersonMediaResponse> media
) {
}
