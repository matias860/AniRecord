package com.animelist.backend.dto.anilist;

import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.ReleaseSeason;
import com.animelist.backend.enums.ReleaseStatus;
import java.time.LocalDate;
import java.util.List;

public record AniListMediaResponse(
        Integer anilistId,
        MediaType mediaType,
        String mediaFormat,
        String countryOfOrigin,
        String title,
        String romajiTitle,
        String coverImage,
        String bannerImage,
        String musicVideoUrl,
        List<MediaStreamingLinkResponse> streamingLinks,
        String synopsis,
        Integer totalUnits,
        LocalDate releaseStartDate,
        LocalDate releaseEndDate,
        ReleaseStatus releaseStatus,
        ReleaseSeason releaseSeason,
        Integer releaseYear,
        Integer favourites,
        AiringInfoResponse nextAiringEpisode,
        List<MediaCreditResponse> credits,
        List<MediaRelationResponse> relations,
        List<MediaCharacterResponse> characters,
        List<String> genres,
        List<MediaTagResponse> tags,
        Integer releaseEndYear,
        MediaInsightsResponse insights,
        String englishTitle,
        String nativeTitle,
        Boolean adultContent
) {
    public AniListMediaResponse(
            Integer anilistId, MediaType mediaType, String mediaFormat, String countryOfOrigin,
            String title, String romajiTitle, String coverImage, String bannerImage,
            String musicVideoUrl, List<MediaStreamingLinkResponse> streamingLinks, String synopsis,
            Integer totalUnits, LocalDate releaseStartDate, LocalDate releaseEndDate,
            ReleaseStatus releaseStatus, ReleaseSeason releaseSeason, Integer releaseYear,
            Integer favourites, AiringInfoResponse nextAiringEpisode, List<MediaCreditResponse> credits,
            List<MediaRelationResponse> relations, List<MediaCharacterResponse> characters,
            List<String> genres, List<MediaTagResponse> tags, Integer releaseEndYear,
            MediaInsightsResponse insights
    ) {
        this(anilistId, mediaType, mediaFormat, countryOfOrigin, title, romajiTitle, coverImage,
                bannerImage, musicVideoUrl, streamingLinks, synopsis, totalUnits, releaseStartDate,
                releaseEndDate, releaseStatus, releaseSeason, releaseYear, favourites,
                nextAiringEpisode, credits, relations, characters, genres, tags, releaseEndYear,
                insights, null, null, false);
    }
}
