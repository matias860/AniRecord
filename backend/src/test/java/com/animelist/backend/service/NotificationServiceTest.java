package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.animelist.backend.entity.AppNotification;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.enums.AccountType;
import com.animelist.backend.repository.AppNotificationRepository;
import com.animelist.backend.repository.AppUserRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NotificationServiceTest {
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
        when(notifications.existsByUserIdAndAnilistNotificationId(any(), any()))
                .thenAnswer(invocation -> storedIds.contains(invocation.getArgument(1, Integer.class)));
        when(notifications.save(any(AppNotification.class))).thenAnswer(invocation -> {
            AppNotification notification = invocation.getArgument(0);
            storedIds.add(notification.getAnilistNotificationId());
            return notification;
        });
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

    private String page(int id, boolean hasNextPage) {
        return """
            {"data":{"Page":{"pageInfo":{"hasNextPage":%s},"notifications":[
              {"__typename":"MediaDeletionNotification","id":%d,"deletedMediaTitle":"Obra","createdAt":1}
            ]}}}
            """.formatted(hasNextPage, id);
    }
}
