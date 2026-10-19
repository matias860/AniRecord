package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.animelist.backend.entity.AppNotification;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.enums.AccountType;
import com.animelist.backend.repository.AppNotificationRepository;
import com.animelist.backend.repository.AppUserRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NotificationServiceTest {
    @Test void skipsHistoricalNotificationTypesInsteadOfFailingTheWholeList() {
        AppNotificationRepository notifications = mock(AppNotificationRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        AniListAuthService auth = mock(AniListAuthService.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        AppUser user = new AppUser();
        user.setId(5L);
        when(currentUser.require()).thenReturn(user);

        Object[] invalid = { 1L, "AIRING", null, "Histórica", null, null, null, false, LocalDateTime.now() };
        Object[] valid = { 2L, "AIRING_EPISODE", 20, "Anime", null, "Nuevo episodio", 2, false, LocalDateTime.now() };
        when(notifications.findRowsByUserId(any(), any())).thenReturn(List.of(invalid, valid));
        NotificationService service = new NotificationService(
                notifications, users, auth, currentUser, mock(RestClient.class));

        var result = service.findRecent(10);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).id());
    }

    @Test void reportsFailedSynchronizationWhenAniListCannotBeReached() {
        AppNotificationRepository notifications = mock(AppNotificationRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        AniListAuthService auth = mock(AniListAuthService.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        AppUser user = new AppUser();
        user.setId(6L);
        user.setAccountType(AccountType.ANILIST);
        user.setNotificationsInitializedAt(LocalDateTime.now());
        when(currentUser.require()).thenReturn(user);
        when(auth.validToken(user)).thenThrow(new IllegalStateException("AniList unavailable"));
        NotificationService service = new NotificationService(
                notifications, users, auth, currentUser, mock(RestClient.class));

        NotificationService.SyncResult result = service.syncSafely();

        assertFalse(result.successful());
        assertEquals(0, result.added());
    }

    @Test void importsEveryAiringEpisodeWithANumber() {
        assertTrue(NotificationService.shouldImport("AiringNotification", 1));
        assertTrue(NotificationService.shouldImport("AiringNotification", 2));
        assertFalse(NotificationService.shouldImport("AiringNotification", null));
    }

    @Test void importsNonAiringNotifications() {
        assertTrue(NotificationService.shouldImport("RelatedMediaAdditionNotification", null));
        assertTrue(NotificationService.shouldImport("MediaDataChangeNotification", null));
    }

    @Test void continuesAnExceptionalBacklogAcrossLaterSynchronizations() {
        AppNotificationRepository notifications = mock(AppNotificationRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        AniListAuthService auth = mock(AniListAuthService.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        AppUser user = new AppUser();
        user.setId(7L);
        user.setAccountType(AccountType.ANILIST);
        user.setNotificationsInitializedAt(LocalDateTime.now());
        when(currentUser.require()).thenReturn(user);
        when(auth.validToken()).thenReturn("token");
        Set<Integer> storedIds = new HashSet<>();
        mockBatchStorage(notifications, storedIds, null);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(times(6), requestTo("https://graphql.anilist.co"))
                .andRespond(request -> withSuccess(page(storedIds.size() + 1, storedIds.size() < 5), MediaType.APPLICATION_JSON).createResponse(request));
        NotificationService service = new NotificationService(notifications, users, auth, currentUser, builder.build());

        assertEquals(3, service.sync());
        assertEquals(4, user.getNotificationBackfillPage());
        assertEquals(3, service.sync());
        assertEquals(null, user.getNotificationBackfillPage());
        assertEquals(6, storedIds.size());
        server.verify();
    }

    @Test void doesNotFinishBackfillWhenAnOffsetShiftMixesKnownAndUnknownNotifications() {
        AppNotificationRepository notifications = mock(AppNotificationRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        AniListAuthService auth = mock(AniListAuthService.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        AppUser user = new AppUser();
        user.setId(8L);
        user.setAccountType(AccountType.ANILIST);
        user.setNotificationsInitializedAt(LocalDateTime.now());
        user.setNotificationBackfillPage(4);
        when(currentUser.require()).thenReturn(user);
        when(auth.validToken()).thenReturn("token");
        Set<Integer> storedIds = new HashSet<>(Set.of(200));
        mockBatchStorage(notifications, storedIds, null);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AtomicInteger response = new AtomicInteger();
        server.expect(times(3), requestTo("https://graphql.anilist.co"))
                .andRespond(request -> switch (response.getAndIncrement()) {
                    case 0 -> withSuccess(page(new int[] { 400 }, true), MediaType.APPLICATION_JSON).createResponse(request);
                    case 1 -> withSuccess(page(new int[] { 200, 150 }, true), MediaType.APPLICATION_JSON).createResponse(request);
                    default -> withSuccess(page(new int[] { 100 }, false), MediaType.APPLICATION_JSON).createResponse(request);
                });
        NotificationService service = new NotificationService(notifications, users, auth, currentUser, builder.build());

        assertEquals(3, service.sync());
        assertEquals(null, user.getNotificationBackfillPage());
        assertEquals(Set.of(400, 200, 150, 100), storedIds);
        server.verify();
    }

    @Test void catchesUpMoreThanFiftyRecentNotificationsBeforeAdvancingHistoricalBackfill() {
        AppNotificationRepository notifications = mock(AppNotificationRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        AniListAuthService auth = mock(AniListAuthService.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        AppUser user = backfillingUser(9L, 4);
        when(currentUser.require()).thenReturn(user);
        when(auth.validToken()).thenReturn("token");
        Set<Integer> storedIds = storedNotifications(notifications, 900);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AtomicInteger response = new AtomicInteger();
        int[] firstFifty = IntStream.rangeClosed(1051, 1100).toArray();
        int[] secondFifty = IntStream.rangeClosed(1001, 1050).toArray();
        server.expect(times(3), requestTo("https://graphql.anilist.co"))
                .andRespond(request -> switch (response.getAndIncrement()) {
                    case 0 -> withSuccess(page(firstFifty, true), MediaType.APPLICATION_JSON).createResponse(request);
                    case 1 -> withSuccess(page(secondFifty, true), MediaType.APPLICATION_JSON).createResponse(request);
                    default -> withSuccess(page(new int[] { 900 }, true), MediaType.APPLICATION_JSON).createResponse(request);
                });
        NotificationService service = new NotificationService(notifications, users, auth, currentUser, builder.build());

        assertEquals(100, service.sync());
        assertEquals(6, user.getNotificationBackfillPage());
        assertEquals(null, user.getNotificationCatchupPage());
        assertEquals(null, user.getNotificationCatchupAnchorId());
        assertEquals(101, storedIds.size());
        server.verify();
    }

    @Test void reanchorsCatchupWhenTwoFullPagesArriveBetweenSynchronizations() {
        AppNotificationRepository notifications = mock(AppNotificationRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        AniListAuthService auth = mock(AniListAuthService.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        AppUser user = backfillingUser(10L, 10);
        when(currentUser.require()).thenReturn(user);
        when(auth.validToken()).thenReturn("token");
        Set<Integer> storedIds = storedNotifications(notifications, 900);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AtomicInteger response = new AtomicInteger();
        int[] firstNewPage = IntStream.iterate(1300, id -> id - 1).limit(50).toArray();
        int[] secondNewPage = IntStream.iterate(1250, id -> id - 1).limit(50).toArray();
        server.expect(times(9), requestTo("https://graphql.anilist.co"))
                .andRespond(request -> switch (response.getAndIncrement()) {
                    case 0 -> withSuccess(page(new int[] { 1200 }, true), MediaType.APPLICATION_JSON).createResponse(request);
                    case 1 -> withSuccess(page(new int[] { 1199 }, true), MediaType.APPLICATION_JSON).createResponse(request);
                    case 2 -> withSuccess(page(new int[] { 1198 }, true), MediaType.APPLICATION_JSON).createResponse(request);
                    case 3 -> withSuccess(page(firstNewPage, true), MediaType.APPLICATION_JSON).createResponse(request);
                    case 4 -> withSuccess(page(secondNewPage, true), MediaType.APPLICATION_JSON).createResponse(request);
                    case 5 -> withSuccess(page(new int[] { 1200 }, true), MediaType.APPLICATION_JSON).createResponse(request);
                    case 6 -> withSuccess(page(new int[] { 1300 }, true), MediaType.APPLICATION_JSON).createResponse(request);
                    case 7 -> withSuccess(page(new int[] { 1198, 1197 }, true), MediaType.APPLICATION_JSON).createResponse(request);
                    default -> withSuccess(page(new int[] { 1196, 900 }, true), MediaType.APPLICATION_JSON).createResponse(request);
                });
        NotificationService service = new NotificationService(notifications, users, auth, currentUser, builder.build());

        assertEquals(3, service.sync());
        assertEquals(4, user.getNotificationBackfillPage());
        assertEquals(4, user.getNotificationCatchupPage());
        assertEquals(1200, user.getNotificationCatchupAnchorId());
        assertEquals(100, service.sync());
        assertEquals(6, user.getNotificationBackfillPage());
        assertEquals(6, user.getNotificationCatchupPage());
        assertEquals(1300, user.getNotificationCatchupAnchorId());
        assertEquals(2, service.sync());
        assertEquals(8, user.getNotificationBackfillPage());
        assertEquals(null, user.getNotificationCatchupPage());
        assertEquals(null, user.getNotificationCatchupAnchorId());
        assertEquals(106, storedIds.size());
        assertTrue(storedIds.containsAll(Set.of(900, 1200, 1199, 1198, 1300, 1201, 1197, 1196)));
        server.verify();
    }

    @Test void advancesHistoricalCursorInsteadOfRequestingTheCatchupPageTwice() {
        AppNotificationRepository notifications = mock(AppNotificationRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        AniListAuthService auth = mock(AniListAuthService.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        AppUser user = backfillingUser(11L, 4);
        user.setNotificationCatchupPage(4);
        user.setNotificationCatchupAnchorId(500);
        when(currentUser.require()).thenReturn(user);
        when(auth.validToken()).thenReturn("token");
        Set<Integer> storedIds = storedNotifications(notifications, 500, 400);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://graphql.anilist.co"))
                .andExpect(content().json("{\"variables\":{\"page\":1}}"))
                .andRespond(withSuccess(page(new int[] { 500 }, true), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://graphql.anilist.co"))
                .andExpect(content().json("{\"variables\":{\"page\":4}}"))
                .andRespond(withSuccess(page(new int[] { 450, 400 }, true), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://graphql.anilist.co"))
                .andExpect(content().json("{\"variables\":{\"page\":5}}"))
                .andRespond(withSuccess(page(new int[] { 350 }, false), MediaType.APPLICATION_JSON));
        NotificationService service = new NotificationService(notifications, users, auth, currentUser, builder.build());

        assertEquals(2, service.sync());
        assertEquals(null, user.getNotificationBackfillPage());
        assertEquals(null, user.getNotificationCatchupPage());
        assertEquals(null, user.getNotificationCatchupAnchorId());
        assertEquals(Set.of(500, 450, 400, 350), storedIds);
        server.verify();
    }

    @Test void initialSynchronizationContinuesItsHistoryAndKeepsItRead() {
        AppNotificationRepository notifications = mock(AppNotificationRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        AniListAuthService auth = mock(AniListAuthService.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        AppUser user = new AppUser();
        user.setId(12L); user.setAccountType(AccountType.ANILIST);
        when(currentUser.require()).thenReturn(user);
        when(auth.validToken()).thenReturn("token");
        Set<Integer> storedIds = new HashSet<>();
        Map<Integer, Boolean> readState = new HashMap<>();
        mockBatchStorage(notifications, storedIds, readState);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AtomicInteger response = new AtomicInteger();
        server.expect(times(8), requestTo("https://graphql.anilist.co"))
                .andRespond(request -> switch (response.getAndIncrement()) {
                    case 0 -> withSuccess(page(6, true), MediaType.APPLICATION_JSON).createResponse(request);
                    case 1 -> withSuccess(page(5, true), MediaType.APPLICATION_JSON).createResponse(request);
                    case 2 -> withSuccess(page(4, true), MediaType.APPLICATION_JSON).createResponse(request);
                    case 3, 6 -> withSuccess(page(6, true), MediaType.APPLICATION_JSON).createResponse(request);
                    case 4 -> withSuccess(page(3, true), MediaType.APPLICATION_JSON).createResponse(request);
                    case 5 -> withSuccess(page(2, true), MediaType.APPLICATION_JSON).createResponse(request);
                    default -> withSuccess(page(1, false), MediaType.APPLICATION_JSON).createResponse(request);
                });
        NotificationService service = new NotificationService(notifications, users, auth, currentUser, builder.build());

        assertEquals(3, service.initialSync());
        assertEquals(4, user.getNotificationBackfillPage());
        assertEquals(Boolean.TRUE, user.getNotificationBackfillAsRead());
        assertEquals(2, service.sync());
        assertEquals(6, user.getNotificationBackfillPage());
        assertEquals(1, service.sync());

        assertEquals(null, user.getNotificationBackfillPage());
        assertEquals(null, user.getNotificationBackfillAsRead());
        assertEquals(Set.of(1, 2, 3, 4, 5, 6), storedIds);
        assertTrue(readState.values().stream().allMatch(Boolean.TRUE::equals));
        server.verify();
    }

    @Test void mergesAnAniListAiringNoticeIntoTheExistingLocalEpisodeNotice() {
        AppNotificationRepository notifications = mock(AppNotificationRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        AniListAuthService auth = mock(AniListAuthService.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        AppUser user = new AppUser();
        user.setId(13L);
        user.setAccountType(AccountType.ANILIST);
        user.setNotificationsInitializedAt(LocalDateTime.now());
        AppNotification local = new AppNotification();
        local.setId(31L);
        local.setUser(user);
        local.setSourceKey("AIRING:99:4");
        local.setRead(false);
        when(currentUser.require()).thenReturn(user);
        when(auth.validToken()).thenReturn("token");
        when(notifications.findByUserIdAndAnilistNotificationIdIn(any(), any())).thenReturn(List.of());
        when(notifications.findByUserIdAndSourceKeyIn(any(), any())).thenReturn(List.of(local));
        when(notifications.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://graphql.anilist.co")).andRespond(withSuccess("""
            {"data":{"Page":{"pageInfo":{"hasNextPage":false},"notifications":[{
              "__typename":"AiringNotification","id":700,"animeId":99,"episode":4,"createdAt":1,
              "media":{"id":99,"title":{"userPreferred":"Serie"},"coverImage":{"large":"cover"}}
            }]}}}
            """, MediaType.APPLICATION_JSON));
        NotificationService service = new NotificationService(notifications, users, auth, currentUser, builder.build());

        assertEquals(0, service.sync());
        assertEquals(700, local.getAnilistNotificationId());
        verify(notifications).saveAll(any());
        server.verify();
    }

    private AppUser backfillingUser(long id, int page) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setAccountType(AccountType.ANILIST);
        user.setNotificationsInitializedAt(LocalDateTime.now());
        user.setNotificationBackfillPage(page);
        return user;
    }

    private Set<Integer> storedNotifications(AppNotificationRepository notifications, Integer... existingIds) {
        Set<Integer> storedIds = new HashSet<>(Set.of(existingIds));
        mockBatchStorage(notifications, storedIds, null);
        return storedIds;
    }

    @SuppressWarnings("unchecked")
    private void mockBatchStorage(AppNotificationRepository notifications, Set<Integer> storedIds,
            Map<Integer, Boolean> readState) {
        when(notifications.findByUserIdAndAnilistNotificationIdIn(any(), any())).thenAnswer(invocation -> {
            java.util.Collection<Integer> requested = invocation.getArgument(1);
            return requested.stream().filter(storedIds::contains).map(id -> {
                AppNotification notification = new AppNotification();
                notification.setId(id.longValue());
                notification.setAnilistNotificationId(id);
                return notification;
            }).toList();
        });
        when(notifications.findByUserIdAndSourceKeyIn(any(), any())).thenReturn(List.of());
        when(notifications.saveAll(any())).thenAnswer(invocation -> {
            Iterable<AppNotification> saved = invocation.getArgument(0);
            saved.forEach(notification -> {
                storedIds.add(notification.getAnilistNotificationId());
                if (readState != null) readState.put(notification.getAnilistNotificationId(), notification.getRead());
            });
            return saved;
        });
    }

    private String page(int id, boolean hasNextPage) {
        return page(new int[] { id }, hasNextPage);
    }

    private String page(int[] ids, boolean hasNextPage) {
        String notifications = java.util.Arrays.stream(ids)
                .mapToObj(id -> "{\"__typename\":\"MediaDeletionNotification\",\"id\":" + id
                        + ",\"deletedMediaTitle\":\"Obra\",\"createdAt\":1}")
                .collect(java.util.stream.Collectors.joining(","));
        return """
            {"data":{"Page":{"pageInfo":{"hasNextPage":%s},"notifications":[
              %s
            ]}}}
            """.formatted(hasNextPage, notifications);
    }
}
