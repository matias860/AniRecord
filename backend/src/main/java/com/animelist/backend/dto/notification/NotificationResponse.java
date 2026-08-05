package com.animelist.backend.dto.notification;

import com.animelist.backend.enums.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(Long id, NotificationType type, Integer anilistMediaId,
        String title, String coverImage, String message, Integer episode, boolean read,
        LocalDateTime occurredAt) {}
