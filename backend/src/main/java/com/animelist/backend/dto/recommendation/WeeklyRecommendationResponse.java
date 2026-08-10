package com.animelist.backend.dto.recommendation;

import com.animelist.backend.enums.MediaType;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record WeeklyRecommendationResponse(
        Long id,
        LocalDate weekStart,
        LocalDateTime recommendedAt,
        Long recommenderId,
        String recommenderName,
        String recommenderAvatar,
        Integer anilistId,
        MediaType mediaType,
        String title,
        String romajiTitle,
        String coverImage,
        String bannerImage
) {
}
