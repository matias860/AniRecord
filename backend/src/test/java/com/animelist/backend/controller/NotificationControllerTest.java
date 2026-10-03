package com.animelist.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.animelist.backend.dto.notification.NotificationResponse;
import com.animelist.backend.service.NotificationService;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationControllerTest {
    @Test
    void recentReturnsStoredNotificationsWithoutWaitingForAniList() {
        NotificationService service = mock(NotificationService.class);
        NotificationController controller = new NotificationController(service);
        when(service.findRecent(10)).thenReturn(List.of());

        assertEquals(List.of(), controller.recent(10));

        verify(service, never()).syncSafely();
        verify(service).findRecent(10);
    }

    @Test
    void syncRefreshesAniListAndReturnsTheUpdatedStoredNotifications() {
        NotificationService service = mock(NotificationService.class);
        NotificationController controller = new NotificationController(service);
        List<NotificationResponse> stored = List.of();
        when(service.findRecent(10)).thenReturn(stored);

        assertEquals(stored, controller.sync(10));

        verify(service).syncSafely();
        verify(service).findRecent(10);
    }
}
