package com.animelist.backend.dto.library;

import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.ReleaseSeason;
import com.animelist.backend.enums.ReleaseStatus;
import com.animelist.backend.enums.TrackingStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record LibraryEntrySummaryResponse(
        Long id,
        Integer anilistId,
        MediaType mediaType,
        String mediaFormat,
        String countryOfOrigin,
        String title,
        String romajiTitle,
        String coverImage,
        String genres,
        String tags,
        Integer totalUnits,
        ReleaseStatus releaseStatus,
        ReleaseSeason releaseSeason,
        Integer releaseYear,
        Integer progress,
        BigDecimal personalScore,
        Integer rewatchCount,
        TrackingStatus trackingStatus,
        LocalDate startedAt,
        LocalDate completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String englishTitle,
        String nativeTitle,
        Boolean adultContent
) {
    public LibraryEntrySummaryResponse(
            Long id,
            Integer anilistId,
            MediaType mediaType,
            String mediaFormat,
            String countryOfOrigin,
            String title,
            String romajiTitle,
            String coverImage,
            String genres,
            String tags,
            Integer totalUnits,
            ReleaseStatus releaseStatus,
            ReleaseSeason releaseSeason,
            Integer releaseYear,
            Integer progress,
            BigDecimal personalScore,
            Integer rewatchCount,
            TrackingStatus trackingStatus,
            LocalDate startedAt,
            LocalDate completedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(id, anilistId, mediaType, mediaFormat, countryOfOrigin, title, romajiTitle, coverImage,
                genres, tags, totalUnits, releaseStatus, releaseSeason, releaseYear, progress,
                personalScore, rewatchCount, trackingStatus, startedAt, completedAt, createdAt, updatedAt,
                null, null, false);
    }
}
