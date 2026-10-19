package com.animelist.backend.service;

import com.animelist.backend.entity.AppNotification;
import com.animelist.backend.entity.PushDelivery;
import com.animelist.backend.entity.PushSubscription;
import com.animelist.backend.enums.NotificationType;
import com.animelist.backend.repository.AppNotificationRepository;
import com.animelist.backend.repository.PushDeliveryRepository;
import com.animelist.backend.repository.PushSubscriptionRepository;
import com.animelist.backend.repository.TopNineSubmissionRepository;
import com.animelist.backend.repository.WeeklyRecommendationRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CommunityPushService {
    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private final PushSubscriptionRepository subscriptions;
    private final PushDeliveryRepository deliveries;
    private final AppNotificationRepository notifications;
    private final TopNineSubmissionRepository topNineSubmissions;
    private final WeeklyRecommendationRepository recommendations;
    private final WebPushGateway gateway;
    private final Clock clock;

    @Autowired
    public CommunityPushService(PushSubscriptionRepository subscriptions, PushDeliveryRepository deliveries,
            AppNotificationRepository notifications, TopNineSubmissionRepository topNineSubmissions,
            WeeklyRecommendationRepository recommendations, WebPushGateway gateway) {
        this(subscriptions, deliveries, notifications, topNineSubmissions, recommendations,
                gateway, Clock.system(ARGENTINA));
    }

    CommunityPushService(PushSubscriptionRepository subscriptions, PushDeliveryRepository deliveries,
            AppNotificationRepository notifications, TopNineSubmissionRepository topNineSubmissions,
            WeeklyRecommendationRepository recommendations, WebPushGateway gateway, Clock clock) {
        this.subscriptions = subscriptions;
        this.deliveries = deliveries;
        this.notifications = notifications;
        this.topNineSubmissions = topNineSubmissions;
        this.recommendations = recommendations;
        this.gateway = gateway;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${app.community-push.initial-delay:PT5M}",
            fixedDelayString = "${app.community-push.delay:PT30M}")
    public void publishAvailablePrompts() {
        if (!gateway.available()) return;
        Map<Long, List<PushSubscription>> mobileByUser = subscriptions.findByEnabledTrue().stream()
                .filter(this::isMobile)
                .collect(Collectors.groupingBy(subscription -> subscription.getUser().getId()));
        if (mobileByUser.isEmpty()) return;

        LocalDate today = LocalDate.now(clock);
        TopNinePromptCatalog.Period topNinePeriod = TopNinePromptCatalog.current(today);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<Long> userIds = List.copyOf(mobileByUser.keySet());
        Set<Long> topNinePublished = new HashSet<>(topNineSubmissions
                .findUserIdsByPeriodStartAndUserIdIn(topNinePeriod.startsAt(), userIds));
        Set<Long> recommendationPublished = new HashSet<>(recommendations
                .findUserIdsByWeekStartAndUserIdIn(weekStart, userIds));
        List<PromptNotice> notices = new ArrayList<>();
        for (Long userId : userIds) {
            if (!topNinePublished.contains(userId)) notices.add(new PromptNotice(userId,
                    NotificationType.TOP_NINE_AVAILABLE, "TOP_NINE_AVAILABLE:" + topNinePeriod.startsAt(),
                    "Nuevo 3×3 disponible", "Ya podés armar “" + topNinePeriod.prompt().title() + "”."));
            if (!recommendationPublished.contains(userId)) notices.add(new PromptNotice(userId,
                    NotificationType.WEEKLY_RECOMMENDATION_AVAILABLE, "WEEKLY_RECOMMENDATION_AVAILABLE:" + weekStart,
                    "Nueva recomendación semanal", "Ya podés elegir una serie para recomendar esta semana."));
        }
        queue(mobileByUser, notices);
    }

    private void queue(Map<Long, List<PushSubscription>> subscriptionsByUser, List<PromptNotice> notices) {
        if (notices.isEmpty()) return;
        List<Long> userIds = notices.stream().map(PromptNotice::userId).distinct().toList();
        List<String> sourceKeys = notices.stream().map(PromptNotice::sourceKey).distinct().toList();
        Map<String, AppNotification> notificationsByKey = notifications
                .findByUserIdInAndSourceKeyIn(userIds, sourceKeys).stream()
                .collect(Collectors.toMap(item -> noticeKey(item.getUser().getId(), item.getSourceKey()), item -> item));
        List<AppNotification> newNotifications = new ArrayList<>();
        for (PromptNotice notice : notices) {
            String key = noticeKey(notice.userId(), notice.sourceKey());
            if (notificationsByKey.containsKey(key)) continue;
            List<PushSubscription> userSubscriptions = subscriptionsByUser.get(notice.userId());
            AppNotification created = new AppNotification();
            created.setUser(userSubscriptions.get(0).getUser());
            created.setSourceKey(notice.sourceKey());
            created.setNotificationType(notice.type());
            created.setTitle(notice.title());
            created.setMessage(notice.message());
            created.setRead(false);
            created.setOccurredAt(LocalDateTime.now(clock));
            newNotifications.add(created);
            notificationsByKey.put(key, created);
        }
        if (!newNotifications.isEmpty()) notifications.saveAll(newNotifications);

        List<Long> subscriptionIds = subscriptionsByUser.values().stream().flatMap(List::stream)
                .map(PushSubscription::getId).toList();
        List<Long> notificationIds = notices.stream()
                .map(notice -> notificationsByKey.get(noticeKey(notice.userId(), notice.sourceKey())).getId())
                .distinct().toList();
        Set<String> existingDeliveries = notificationIds.isEmpty() ? Set.of()
                : deliveries.findBySubscriptionIdInAndNotificationIdIn(subscriptionIds, notificationIds).stream()
                        .map(item -> item.getSubscription().getId() + ":" + item.getNotification().getId())
                        .collect(Collectors.toSet());
        List<PushDelivery> newDeliveries = new ArrayList<>();
        for (PromptNotice notice : notices) {
            AppNotification notification = notificationsByKey.get(noticeKey(notice.userId(), notice.sourceKey()));
            for (PushSubscription subscription : subscriptionsByUser.get(notice.userId())) {
                if (existingDeliveries.contains(subscription.getId() + ":" + notification.getId())) continue;
                PushDelivery delivery = new PushDelivery();
                delivery.setSubscription(subscription);
                delivery.setNotification(notification);
                newDeliveries.add(delivery);
            }
        }
        if (!newDeliveries.isEmpty()) deliveries.saveAll(newDeliveries);
    }

    private String noticeKey(Long userId, String sourceKey) { return userId + ":" + sourceKey; }
    private record PromptNotice(Long userId, NotificationType type, String sourceKey, String title, String message) {}

    private boolean isMobile(PushSubscription subscription) {
        String userAgent = subscription.getUserAgent();
        if (userAgent == null) return false;
        String normalized = userAgent.toLowerCase(Locale.ROOT);
        return normalized.contains("android") || normalized.contains("iphone") || normalized.contains("ipad")
                || normalized.contains("ipod") || normalized.contains("mobile");
    }
}
