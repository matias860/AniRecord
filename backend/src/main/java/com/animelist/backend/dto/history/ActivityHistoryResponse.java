package com.animelist.backend.dto.history;

import com.animelist.backend.enums.ActivityType;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.TrackingStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ActivityHistoryResponse(
        Long id,
        Integer anilistId,
        MediaType mediaType,
        String title,
        String coverImage,
        ActivityType activityType,
        Integer previousProgress,
        Integer newProgress,
        TrackingStatus previousStatus,
        TrackingStatus newStatus,
        BigDecimal previousScore,
        BigDecimal newScore,
        Integer previousRewatchCount,
        Integer newRewatchCount,
        LocalDateTime createdAt
) {
}
