package com.animelist.backend.dto.anilist;

import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.ReleaseSeason;
import com.animelist.backend.enums.ReleaseStatus;
import java.time.LocalDate;
import java.util.List;

public record AniListMediaResponse(
        Integer anilistId,
        MediaType mediaType,
        String title,
        String coverImage,
        String bannerImage,
        String synopsis,
        Integer totalUnits,
        LocalDate releaseStartDate,
        LocalDate releaseEndDate,
        ReleaseStatus releaseStatus,
        ReleaseSeason releaseSeason,
        Integer releaseYear,
        Integer favourites,
        AiringInfoResponse nextAiringEpisode,
        List<MediaRelationResponse> relations,
        List<MediaCharacterResponse> characters,
        List<MediaTagResponse> tags
) {
}
