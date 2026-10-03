package com.animelist.backend.dto.notification;

import com.animelist.backend.enums.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(Long id, NotificationType type, Integer anilistMediaId,
        String title, String coverImage, String message, Integer episode, Boolean read,
        LocalDateTime occurredAt) {
    /**
     * Hibernate exposes nullable entity booleans as {@link Boolean} in JPQL
     * constructor projections. Keep that persistence concern out of the API and
     * normalize historical nulls to unread.
     */
    public NotificationResponse {
        read = Boolean.TRUE.equals(read);
    }
}
