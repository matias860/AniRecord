package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.animelist.backend.entity.AppNotification;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.entity.PushDelivery;
import com.animelist.backend.entity.PushSubscription;
import com.animelist.backend.enums.NotificationType;
import com.animelist.backend.repository.AppNotificationRepository;
import com.animelist.backend.repository.PushDeliveryRepository;
import com.animelist.backend.repository.PushSubscriptionRepository;
import com.animelist.backend.repository.TopNineSubmissionRepository;
import com.animelist.backend.repository.WeeklyRecommendationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CommunityPushServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-16T13:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));
    private final PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
    private final PushDeliveryRepository deliveries = mock(PushDeliveryRepository.class);
    private final AppNotificationRepository notifications = mock(AppNotificationRepository.class);
    private final TopNineSubmissionRepository topNine = mock(TopNineSubmissionRepository.class);
    private final WeeklyRecommendationRepository recommendations = mock(WeeklyRecommendationRepository.class);
    private final WebPushGateway gateway = mock(WebPushGateway.class);
    private final PushDeliveryService deliveryService = mock(PushDeliveryService.class);
    private final CommunityPushService service = new CommunityPushService(
            subscriptions, deliveries, notifications, topNine, recommendations, gateway, deliveryService, CLOCK);

    @Test
    void queuesNewTopNineAndWeeklyRecommendationForMobileDevices() {
        AppUser user = new AppUser();
        user.setId(7L);
        PushSubscription subscription = subscription(user, 11L,
                "Mozilla/5.0 (Linux; Android 16; Mobile) Chrome/152");
        when(gateway.available()).thenReturn(true);
        when(subscriptions.findByEnabledTrue()).thenReturn(List.of(subscription));
        when(topNine.findUserIdsByPeriodStartAndUserIdIn(any(), any())).thenReturn(List.of());
        when(recommendations.findUserIdsByWeekStartAndUserIdIn(any(), any())).thenReturn(List.of());
        when(notifications.findByUserIdInAndSourceKeyIn(any(), any())).thenReturn(List.of());
        List<AppNotification> savedNotifications = new ArrayList<>();
        when(notifications.saveAll(any())).thenAnswer(invocation -> {
            Iterable<AppNotification> batch = invocation.getArgument(0);
            batch.forEach(notification -> {
                notification.setId((long) savedNotifications.size() + 1);
                savedNotifications.add(notification);
            });
            return savedNotifications;
        });
        when(deliveries.findBySubscriptionIdInAndNotificationIdIn(any(), any())).thenReturn(List.of());

        service.publishAvailablePrompts();

        assertEquals(List.of(NotificationType.TOP_NINE_AVAILABLE, NotificationType.WEEKLY_RECOMMENDATION_AVAILABLE),
                savedNotifications.stream().map(AppNotification::getNotificationType).toList());
        assertTrue(savedNotifications.get(0).getSourceKey().endsWith("2026-10-16"));
        assertTrue(savedNotifications.get(1).getSourceKey().endsWith("2026-10-12"));
        verify(deliveries).saveAll(any());
        verify(deliveryService).deliverNow();
    }

    @Test
    void ignoresDesktopSubscriptionsForCommunityReminders() {
        AppUser user = new AppUser();
        user.setId(7L);
        when(gateway.available()).thenReturn(true);
        when(subscriptions.findByEnabledTrue()).thenReturn(List.of(subscription(user, 11L,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/152")));

        service.publishAvailablePrompts();

        verify(notifications, never()).saveAll(any());
        verify(deliveries, never()).saveAll(any());
    }

    private PushSubscription subscription(AppUser user, Long id, String userAgent) {
        PushSubscription subscription = new PushSubscription();
        subscription.setId(id);
        subscription.setUser(user);
        subscription.setUserAgent(userAgent);
        subscription.setEnabled(true);
        return subscription;
    }
}
