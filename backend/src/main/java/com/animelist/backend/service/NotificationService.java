package com.animelist.backend.service;

import com.animelist.backend.dto.notification.NotificationResponse;
import com.animelist.backend.entity.AppNotification;
import com.animelist.backend.enums.NotificationType;
import com.animelist.backend.enums.AccountType;
import com.animelist.backend.exception.BusinessException;
import com.animelist.backend.repository.AppNotificationRepository;
import com.animelist.backend.repository.AppUserRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Duration;
import java.net.http.HttpClient;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class NotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_PAGES_PER_SYNC = 3;
    private static final String QUERY = """
        query($page: Int!) { Page(page: $page, perPage: 50) {
          pageInfo { hasNextPage }
          notifications(type_in: [AIRING, RELATED_MEDIA_ADDITION, MEDIA_DATA_CHANGE, MEDIA_MERGE, MEDIA_DELETION]) {
          __typename
          ... on AiringNotification { id type animeId episode contexts createdAt media { id title { userPreferred } coverImage { large } } }
          ... on RelatedMediaAdditionNotification { id type mediaId context createdAt media { id title { userPreferred } coverImage { large } } }
          ... on MediaDataChangeNotification { id type mediaId context reason createdAt media { id title { userPreferred } coverImage { large } } }
          ... on MediaMergeNotification { id type mediaId context reason createdAt media { id title { userPreferred } coverImage { large } } }
          ... on MediaDeletionNotification { id type deletedMediaTitle context reason createdAt }
        } } }
        """;
    private final AppNotificationRepository repository;
    private final AppUserRepository users;
    private final AniListAuthService auth;
    private final CurrentUserService currentUser;
    private final RestClient client;
    public NotificationService(AppNotificationRepository repository, AppUserRepository users, AniListAuthService auth, CurrentUserService currentUser) {
        this(repository, users, auth, currentUser, notificationClient());
    }

    NotificationService(AppNotificationRepository repository, AppUserRepository users, AniListAuthService auth,
            CurrentUserService currentUser, RestClient client) {
        this.repository = repository;
        this.users = users;
        this.auth = auth;
        this.currentUser = currentUser;
        this.client = client;
    }

    private static RestClient notificationClient() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    public int syncSafely() {
        try {
            var user = currentUser.require();
            if (user.getAccountType() == AccountType.LOCAL) return 0;
            return user.getNotificationsInitializedAt() == null ? initialSync() : sync();
        }
        catch (RuntimeException exception) {
            LOGGER.warn("No se pudieron sincronizar las notificaciones de AniList; se conserva el historial local", exception);
            return 0;
        }
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public int sync() {
        return sync(false);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public int initialSync() {
        return sync(true);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    private int sync(boolean markImportedAsRead) {
        var user=currentUser.require();
        String token = auth.validToken(); if (token == null) return 0;
        Integer originalBackfillPage = user.getNotificationBackfillPage();
        Integer originalCatchupPage = user.getNotificationCatchupPage();
        Integer originalCatchupAnchorId = user.getNotificationCatchupAnchorId();
        int added = !markImportedAsRead && originalBackfillPage != null
                ? syncDuringBackfill(user, token)
                : syncSequentially(user, token, markImportedAsRead);
        if (markImportedAsRead) {
            user.setNotificationsInitializedAt(java.time.LocalDateTime.now());
        }
        if (!markImportedAsRead && originalBackfillPage == null && user.getNotificationBackfillPage() != null) {
            LOGGER.info("Se inició el backfill de notificaciones de AniList: userId={}, nextPage={}",
                    user.getId(), user.getNotificationBackfillPage());
        } else if (!markImportedAsRead && originalBackfillPage != null && user.getNotificationBackfillPage() == null) {
            LOGGER.info("Se completó el backfill de notificaciones de AniList: userId={}", user.getId());
        }
        if (!markImportedAsRead && originalCatchupPage == null && user.getNotificationCatchupPage() != null) {
            LOGGER.info("Se inició el catch-up reciente de notificaciones de AniList: userId={}, nextPage={}",
                    user.getId(), user.getNotificationCatchupPage());
        } else if (!markImportedAsRead && originalCatchupPage != null && user.getNotificationCatchupPage() == null) {
            LOGGER.info("Se completó el catch-up reciente de notificaciones de AniList: userId={}", user.getId());
        } else if (!markImportedAsRead && user.getNotificationCatchupPage() != null
                && !Objects.equals(originalCatchupPage, user.getNotificationCatchupPage())) {
            LOGGER.info("Se pausó el catch-up reciente de notificaciones de AniList: userId={}, nextPage={}",
                    user.getId(), user.getNotificationCatchupPage());
        }
        if (markImportedAsRead
                || !Objects.equals(originalBackfillPage, user.getNotificationBackfillPage())
                || !Objects.equals(originalCatchupPage, user.getNotificationCatchupPage())
                || !Objects.equals(originalCatchupAnchorId, user.getNotificationCatchupAnchorId())) users.save(user);
        return added;
    }

    private int syncSequentially(com.animelist.backend.entity.AppUser user, String token, boolean markImportedAsRead) {
        int added = 0;
        int pageNumber = 1;
        PageResult result = null;
        for (int request = 0; request < MAX_PAGES_PER_SYNC; request++, pageNumber++) {
            result = syncPage(user, token, pageNumber, markImportedAsRead);
            added += result.added();
            if (result.reachedKnownBoundary() || !result.hasNextPage()) break;
        }
        boolean unresolved = result != null && !result.reachedKnownBoundary() && result.hasNextPage();
        user.setNotificationBackfillPage(!markImportedAsRead && unresolved ? pageNumber : null);
        user.setNotificationCatchupPage(null);
        user.setNotificationCatchupAnchorId(null);
        return added;
    }

    private int syncDuringBackfill(com.animelist.backend.entity.AppUser user, String token) {
        int added = 0;
        int requests = 0;
        PageResult firstPage = syncPage(user, token, 1, false);
        added += firstPage.added();
        requests++;
        if (!firstPage.hasNextPage()) {
            clearNotificationCursors(user);
            return added;
        }

        int backfillPage = user.getNotificationBackfillPage();
        Integer catchupPage = user.getNotificationCatchupPage();
        Integer currentHeadId = firstPage.firstNotificationId();
        Integer storedAnchorId = user.getNotificationCatchupAnchorId();

        if (catchupPage != null && storedAnchorId != null) {
            if (!Objects.equals(currentHeadId, storedAnchorId)) {
                int anchorPage = firstPage.notificationIds().contains(storedAnchorId) ? 1 : 0;
                int probePage = 2;
                while (anchorPage == 0 && requests < MAX_PAGES_PER_SYNC) {
                    PageResult probe = syncPage(user, token, probePage, false);
                    added += probe.added();
                    requests++;
                    if (probe.notificationIds().contains(storedAnchorId)) anchorPage = probePage;
                    if (!probe.hasNextPage()) {
                        clearNotificationCursors(user);
                        return added;
                    }
                    probePage++;
                }
                if (anchorPage == 0) {
                    resetNotificationCursors(user, currentHeadId);
                    return added;
                }
                int pageShift = anchorPage - 1;
                catchupPage += pageShift;
                backfillPage += pageShift;
            }
        } else {
            catchupPage = null;
            boolean recentResolved = firstPage.reachedKnownBoundary();
            int boundaryPage = 1;
            int recentPage = 2;
            while (!recentResolved && requests < MAX_PAGES_PER_SYNC) {
                PageResult recent = syncPage(user, token, recentPage, false);
                added += recent.added();
                requests++;
                if (!recent.hasNextPage()) {
                    clearNotificationCursors(user);
                    return added;
                }
                recentResolved = recent.reachedKnownBoundary();
                if (recentResolved) boundaryPage = recentPage;
                recentPage++;
            }
            if (!recentResolved) {
                resetNotificationCursors(user, currentHeadId);
                return added;
            }
            backfillPage += boundaryPage - 1;
        }

        user.setNotificationBackfillPage(backfillPage);
        if (catchupPage != null) {
            int firstCatchupPage = catchupPage;
            int lastCatchupPage = catchupPage - 1;
            boolean catchupResolved = false;
            while (!catchupResolved && requests < MAX_PAGES_PER_SYNC) {
                PageResult result = syncPage(user, token, catchupPage, false);
                added += result.added();
                requests++;
                lastCatchupPage = catchupPage;
                if (!result.hasNextPage()) {
                    clearNotificationCursors(user);
                    return added;
                }
                catchupResolved = result.reachedKnownBoundary();
                if (!catchupResolved) catchupPage++;
            }
            if (!catchupResolved) {
                user.setNotificationCatchupPage(catchupPage);
                user.setNotificationCatchupAnchorId(currentHeadId);
                return added;
            }
            user.setNotificationCatchupPage(null);
            user.setNotificationCatchupAnchorId(null);
            if (backfillPage >= firstCatchupPage && backfillPage <= lastCatchupPage) {
                backfillPage = lastCatchupPage + 1;
                user.setNotificationBackfillPage(backfillPage);
            }
        }

        while (requests < MAX_PAGES_PER_SYNC) {
            PageResult result = syncPage(user, token, backfillPage, false);
            added += result.added();
            requests++;
            if (!result.hasNextPage()) {
                user.setNotificationBackfillPage(null);
                break;
            }
            backfillPage++;
            user.setNotificationBackfillPage(backfillPage);
        }
        return added;
    }

    private void clearNotificationCursors(com.animelist.backend.entity.AppUser user) {
        user.setNotificationCatchupPage(null);
        user.setNotificationCatchupAnchorId(null);
        user.setNotificationBackfillPage(null);
    }

    private void resetNotificationCursors(com.animelist.backend.entity.AppUser user, Integer currentHeadId) {
        user.setNotificationCatchupPage(MAX_PAGES_PER_SYNC + 1);
        user.setNotificationCatchupAnchorId(currentHeadId);
        user.setNotificationBackfillPage(MAX_PAGES_PER_SYNC + 1);
    }

    @SuppressWarnings("unchecked")
    private PageResult syncPage(com.animelist.backend.entity.AppUser user, String token, int pageNumber, boolean markImportedAsRead) {
        Map<String, Object> body = client.post().uri("https://graphql.anilist.co").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", QUERY, "variables", Map.of("page", pageNumber)))
                .retrieve().body(Map.class);
        if (body == null || !(body.get("data") instanceof Map<?, ?> data) || !(data.get("Page") instanceof Map<?, ?> rawPage)) {
            throw new BusinessException("AniList no devolvió las notificaciones");
        }
        Map<String, Object> page = (Map<String, Object>) rawPage;
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.getOrDefault("notifications", List.of());
        int added = 0;
        boolean reachedKnownBoundary = false;
        Integer firstNotificationId = null;
        List<Integer> notificationIds = new ArrayList<>(items.size());
        for (Map<String, Object> item : items) {
            String typename = String.valueOf(item.get("__typename"));
            Integer episode = number(item.get("episode"));
            Integer externalId = number(item.get("id"));
            if (externalId == null) continue;
            if (firstNotificationId == null) firstNotificationId = externalId;
            notificationIds.add(externalId);
            if (!shouldImport(typename, episode)) continue;
            if (repository.existsByUserIdAndAnilistNotificationId(user.getId(), externalId)) {
                reachedKnownBoundary = true;
                continue;
            }
            reachedKnownBoundary = false;
            AppNotification notification = map(item, typename, episode, externalId);
            if (notification != null) {
                notification.setUser(user);
                notification.setRead(markImportedAsRead);
                repository.save(notification);
                added++;
            }
        }
        Map<String, Object> pageInfo = page.get("pageInfo") instanceof Map<?, ?> info ? (Map<String, Object>) info : Map.of();
        return new PageResult(added, reachedKnownBoundary, Boolean.TRUE.equals(pageInfo.get("hasNextPage")),
                firstNotificationId, List.copyOf(notificationIds));
    }

    private record PageResult(int added, boolean reachedKnownBoundary, boolean hasNextPage,
            Integer firstNotificationId, List<Integer> notificationIds) {}

    static boolean shouldImport(String typename, Integer episode) {
        return !"AiringNotification".equals(typename) || episode != null;
    }

    public List<NotificationResponse> findRecent(int limit) {
        return repository.findByUserIdOrderByOccurredAtDesc(
                currentUser.require().getId(), PageRequest.of(0, Math.min(limit, 100)))
                .stream().map(this::response).toList();
    }
    public long unreadCount() { return repository.countByUserIdAndReadFalse(currentUser.require().getId()); }
    @Transactional public void read(Long id) { Long uid=currentUser.require().getId(); repository.findById(id).filter(n->n.getUser().getId().equals(uid)).ifPresent(item -> { item.setRead(true); repository.save(item); }); }
    @Transactional public void readAll() { repository.markAllAsRead(currentUser.require().getId()); }

    @SuppressWarnings("unchecked")
    private AppNotification map(Map<String, Object> item, String typename, Integer episode, Integer externalId) {
        NotificationType type = switch (typename) {
            case "AiringNotification" -> Objects.equals(episode, 1) ? NotificationType.FIRST_EPISODE : NotificationType.AIRING_EPISODE;
            case "RelatedMediaAdditionNotification" -> NotificationType.RELATED_MEDIA_ADDITION;
            case "MediaDataChangeNotification" -> NotificationType.MEDIA_DATA_CHANGE;
            case "MediaMergeNotification" -> NotificationType.MEDIA_MERGE;
            case "MediaDeletionNotification" -> NotificationType.MEDIA_DELETION;
            default -> null;
        }; if (type == null) return null;
        Map<String, Object> media = item.get("media") instanceof Map ? (Map<String, Object>) item.get("media") : null;
        Map<String, Object> titleMap = media != null && media.get("title") instanceof Map ? (Map<String, Object>) media.get("title") : null;
        Map<String, Object> cover = media != null && media.get("coverImage") instanceof Map ? (Map<String, Object>) media.get("coverImage") : null;
        AppNotification n = new AppNotification(); n.setAnilistNotificationId(externalId); n.setNotificationType(type);
        n.setAnilistMediaId(media == null ? number(item.get("mediaId")) : number(media.get("id")));
        n.setTitle(titleMap == null ? Objects.toString(item.get("deletedMediaTitle"), "Obra eliminada") : Objects.toString(titleMap.get("userPreferred"), "Sin título"));
        n.setCoverImage(cover == null ? null : Objects.toString(cover.get("large"), null)); n.setEpisode(episode);
        n.setMessage(message(item, type)); Number created = (Number) item.get("createdAt");
        n.setOccurredAt(created == null ? LocalDateTime.now() : LocalDateTime.ofInstant(Instant.ofEpochSecond(created.longValue()), ZoneId.systemDefault())); return n;
    }
    private String message(Map<String, Object> item, NotificationType type) { Object context = item.get("context"); Object reason = item.get("reason"); return switch(type) { case FIRST_EPISODE -> "Se estrenó el primer episodio"; case AIRING_EPISODE -> "Se estrenó el episodio " + number(item.get("episode")); case RELATED_MEDIA_ADDITION -> Objects.toString(context, "Se anunció una nueva obra relacionada"); case MEDIA_DATA_CHANGE -> Objects.toString(reason, Objects.toString(context, "AniList actualizó información importante")); case MEDIA_MERGE -> Objects.toString(reason, "AniList fusionó esta ficha"); case MEDIA_DELETION -> Objects.toString(reason, "AniList eliminó esta ficha"); }; }
    private Integer number(Object value) { return value instanceof Number number ? number.intValue() : null; }
    private NotificationResponse response(AppNotification notification) {
        return new NotificationResponse(
                notification.getId(), notification.getNotificationType(), notification.getAnilistMediaId(),
                notification.getTitle(), notification.getCoverImage(), notification.getMessage(),
                notification.getEpisode(), Boolean.TRUE.equals(notification.getRead()), notification.getOccurredAt());
    }
}
