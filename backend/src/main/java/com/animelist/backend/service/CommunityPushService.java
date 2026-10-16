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
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public void publishAvailablePrompts() {
        if (!gateway.available()) return;
        Map<Long, List<PushSubscription>> mobileByUser = subscriptions.findByEnabledTrue().stream()
                .filter(this::isMobile)
                .collect(Collectors.groupingBy(subscription -> subscription.getUser().getId()));
        if (mobileByUser.isEmpty()) return;

        LocalDate today = LocalDate.now(clock);
        TopNinePromptCatalog.Period topNinePeriod = TopNinePromptCatalog.current(today);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (List<PushSubscription> userSubscriptions : mobileByUser.values()) {
            Long userId = userSubscriptions.get(0).getUser().getId();
            if (!topNineSubmissions.existsByUserIdAndPeriodStart(userId, topNinePeriod.startsAt())) {
                queue(userSubscriptions, NotificationType.TOP_NINE_AVAILABLE,
                        "TOP_NINE_AVAILABLE:" + topNinePeriod.startsAt(),
                        "Nuevo 3×3 disponible",
                        "Ya podés armar “" + topNinePeriod.prompt().title() + "”.");
            }
            if (!recommendations.existsByUserIdAndWeekStart(userId, weekStart)) {
                queue(userSubscriptions, NotificationType.WEEKLY_RECOMMENDATION_AVAILABLE,
                        "WEEKLY_RECOMMENDATION_AVAILABLE:" + weekStart,
                        "Nueva recomendación semanal",
                        "Ya podés elegir una serie para recomendar esta semana.");
            }
        }
    }

    private void queue(List<PushSubscription> userSubscriptions, NotificationType type,
            String sourceKey, String title, String message) {
        Long userId = userSubscriptions.get(0).getUser().getId();
        AppNotification notification = notifications.findByUserIdAndSourceKey(userId, sourceKey).orElseGet(() -> {
            AppNotification created = new AppNotification();
            created.setUser(userSubscriptions.get(0).getUser());
            created.setSourceKey(sourceKey);
            created.setNotificationType(type);
            created.setTitle(title);
            created.setMessage(message);
            created.setRead(false);
            created.setOccurredAt(LocalDateTime.now(clock));
            return notifications.save(created);
        });
        for (PushSubscription subscription : userSubscriptions) {
            if (deliveries.existsBySubscriptionIdAndNotificationId(subscription.getId(), notification.getId())) continue;
            PushDelivery delivery = new PushDelivery();
            delivery.setSubscription(subscription);
            delivery.setNotification(notification);
            deliveries.save(delivery);
        }
    }

    private boolean isMobile(PushSubscription subscription) {
        String userAgent = subscription.getUserAgent();
        if (userAgent == null) return false;
        String normalized = userAgent.toLowerCase(Locale.ROOT);
        return normalized.contains("android") || normalized.contains("iphone") || normalized.contains("ipad")
                || normalized.contains("ipod") || normalized.contains("mobile");
    }
}
