package com.animelist.backend.dto.anilist;

import com.animelist.backend.enums.TrackingStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AniListLibraryItem(
        AniListMediaResponse media,
        Integer progress,
        BigDecimal personalScore,
        TrackingStatus trackingStatus,
        LocalDate startedAt,
        LocalDate completedAt
) {
}
