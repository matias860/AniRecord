package com.animelist.backend.dto.notification;

import java.util.List;

public record NotificationSyncResponse(
        int added,
        boolean successful,
        List<NotificationResponse> notifications
) {}
