package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {
    @Test void importsOnlyFirstAiringEpisode() {
        assertTrue(NotificationService.shouldImport("AiringNotification", 1));
        assertFalse(NotificationService.shouldImport("AiringNotification", 2));
        assertFalse(NotificationService.shouldImport("AiringNotification", null));
    }

    @Test void importsNonAiringNotifications() {
        assertTrue(NotificationService.shouldImport("RelatedMediaAdditionNotification", null));
        assertTrue(NotificationService.shouldImport("MediaDataChangeNotification", null));
    }
}
