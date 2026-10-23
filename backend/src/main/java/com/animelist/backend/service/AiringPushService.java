package com.animelist.backend.service;

import com.animelist.backend.dto.anilist.AniListMediaResponse;
import com.animelist.backend.entity.*;
import com.animelist.backend.enums.*;
import com.animelist.backend.repository.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AiringPushService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiringPushService.class);
    private static final List<TrackingStatus> TRACKED = List.of(TrackingStatus.PLANNED, TrackingStatus.IN_PROGRESS);
    private static final List<ReleaseStatus> AIRING = List.of(ReleaseStatus.RELEASING, ReleaseStatus.NOT_YET_RELEASED);
    private final LibraryEntryRepository library;
    private final MediaRepository mediaRepository;
    private final AppNotificationRepository notifications;
    private final PushSubscriptionRepository subscriptions;
    private final PushDeliveryRepository deliveries;
    private final AniListClient aniList;
    private final TransactionTemplate transactions;
    private final WebPushGateway pushGateway;
    private final PushDeliveryService deliveryService;
    private final TaskScheduler taskScheduler;
    private final Object scheduleMonitor = new Object();
    private java.util.concurrent.ScheduledFuture<?> nextPublication;
    private Instant scheduledFor;

    public AiringPushService(LibraryEntryRepository library, MediaRepository mediaRepository,
            AppNotificationRepository notifications, PushSubscriptionRepository subscriptions,
            PushDeliveryRepository deliveries, AniListClient aniList, TransactionTemplate transactions,
            WebPushGateway pushGateway, PushDeliveryService deliveryService, TaskScheduler taskScheduler) {
        this.library = library;
        this.mediaRepository = mediaRepository;
        this.notifications = notifications;
        this.subscriptions = subscriptions;
        this.deliveries = deliveries;
        this.aniList = aniList;
        this.transactions = transactions;
        this.pushGateway = pushGateway;
        this.deliveryService = deliveryService;
        this.taskScheduler = taskScheduler;
    }

    @Scheduled(initialDelayString = "${app.airing-push.refresh-initial-delay:PT3M}", fixedDelayString = "${app.airing-push.refresh-delay:PT6H}")
    public void refreshTrackedSchedules() {
        if (!pushGateway.available()) return;
        refresh(library.findDistinctAiringTrackedMedia(TRACKED, AIRING));
        scheduleNextPublication();
    }

    @Scheduled(initialDelayString = "${app.airing-push.publish-initial-delay:PT4M}", fixedDelayString = "${app.airing-push.recovery-delay:PT6H}")
    public void recoverDueEpisodes() {
        publishDueEpisodes();
    }

    void publishDueEpisodes() {
        if (!pushGateway.available()) return;
        try {
            List<Long> dueIds = mediaRepository.findByNextAiringAtLessThanEqualAndNextAiringEpisodeIsNotNull(Instant.now(), PageRequest.of(0, 100))
                    .stream().map(Media::getId).toList();
            if (dueIds.isEmpty()) return;
            dueIds.forEach(id -> transactions.executeWithoutResult(ignored -> publish(id)));
            refresh(mediaRepository.findAllById(dueIds));
            deliveryService.deliverNow();
        } finally {
            scheduleNextPublication();
        }
    }

    private void scheduleNextPublication() {
        if (!pushGateway.available()) return;
        Instant next = mediaRepository.findFirstByNextAiringAtIsNotNullOrderByNextAiringAtAsc()
                .map(Media::getNextAiringAt).orElse(null);
        synchronized (scheduleMonitor) {
            if (Objects.equals(next, scheduledFor) && nextPublication != null && !nextPublication.isDone()) return;
            if (nextPublication != null) nextPublication.cancel(false);
            nextPublication = null;
            scheduledFor = next;
            if (next == null) return;
            Instant runAt = next.isAfter(Instant.now()) ? next : Instant.now().plusSeconds(1);
            nextPublication = taskScheduler.schedule(() -> {
                synchronized (scheduleMonitor) {
                    nextPublication = null;
                    scheduledFor = null;
                }
                publishDueEpisodes();
            }, runAt);
        }
    }

    private void refresh(List<Media> tracked) {
        if (tracked.isEmpty()) return;
        try {
            Map<Integer, AniListMediaResponse> responses = aniList.getAiringMedia(
                    tracked.stream().map(Media::getAnilistId).distinct().toList()).stream()
                    .collect(Collectors.toMap(AniListMediaResponse::anilistId, Function.identity()));
            Instant checkedAt = Instant.now();
            tracked.forEach(media -> {
                AniListMediaResponse response = responses.get(media.getAnilistId());
                if (response == null || response.nextAiringEpisode() == null) {
                    media.setNextAiringEpisode(null);
                    media.setNextAiringAt(null);
                } else {
                    media.setNextAiringEpisode(response.nextAiringEpisode().episode());
                    media.setNextAiringAt(Instant.ofEpochSecond(response.nextAiringEpisode().airingAt()));
                }
                media.setAiringScheduleCheckedAt(checkedAt);
            });
            mediaRepository.saveAll(tracked);
        } catch (RuntimeException exception) {
            LOGGER.warn("No se pudo actualizar la agenda de emisiones: {}", exception.getClass().getSimpleName());
        }
    }

    private void publish(Long mediaId) {
        Media media = mediaRepository.findForUpdateById(mediaId).orElse(null);
        if (media == null || media.getNextAiringEpisode() == null || Objects.equals(media.getNextAiringEpisode(), media.getLastNotifiedEpisode())) return;
        int episode = media.getNextAiringEpisode();
        String sourceKey = "AIRING:" + media.getAnilistId() + ':' + episode;
        for (AppUser user : library.findDistinctUsersTrackingMedia(mediaId, TRACKED)) {
            AppNotification notification = notifications.findByUserIdAndSourceKey(user.getId(), sourceKey).orElseGet(() -> {
                AppNotification created = new AppNotification();
                created.setUser(user);
                created.setSourceKey(sourceKey);
                created.setNotificationType(episode == 1 ? NotificationType.FIRST_EPISODE : NotificationType.AIRING_EPISODE);
                created.setAnilistMediaId(media.getAnilistId());
                created.setTitle(media.getTitle());
                created.setCoverImage(media.getCoverImage());
                created.setEpisode(episode);
                created.setMessage(episode == 1
                        ? media.getTitle() + " acaba de estrenarse."
                        : "Ya está disponible el episodio " + episode + " de " + media.getTitle() + '.');
                created.setRead(false);
                Instant airingAt = media.getNextAiringAt() == null ? Instant.now() : media.getNextAiringAt();
                created.setOccurredAt(LocalDateTime.ofInstant(airingAt, ZoneOffset.UTC));
                return notifications.save(created);
            });
            for (PushSubscription subscription : subscriptions.findByUserIdAndEnabledTrue(user.getId())) {
                boolean allowed = episode == 1 ? subscription.isPremieresEnabled() : subscription.isEpisodesEnabled();
                if (!allowed || deliveries.existsBySubscriptionIdAndNotificationId(subscription.getId(), notification.getId())) continue;
                PushDelivery delivery = new PushDelivery();
                delivery.setSubscription(subscription);
                delivery.setNotification(notification);
                deliveries.save(delivery);
            }
        }
        media.setLastNotifiedEpisode(episode);
        media.setNextAiringEpisode(null);
        media.setNextAiringAt(null);
        mediaRepository.save(media);
    }
}
