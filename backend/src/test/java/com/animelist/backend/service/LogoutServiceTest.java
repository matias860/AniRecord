package com.animelist.backend.service;

import static org.mockito.Mockito.*;

import com.animelist.backend.entity.AppUser;
import com.animelist.backend.repository.AppSessionRepository;
import com.animelist.backend.repository.PushSubscriptionRepository;
import com.animelist.backend.security.TokenSecurity;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LogoutServiceTest {
    @Test
    void removesThisDevicesPushEndpointBeforeInvalidatingTheSession() {
        AppSessionRepository sessions = mock(AppSessionRepository.class);
        PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        AppUser user = new AppUser();
        user.setId(42L);
        when(currentUser.optional()).thenReturn(Optional.of(user));
        LogoutService service = new LogoutService(sessions, subscriptions, currentUser);

        service.logout("session-token", "https://fcm.googleapis.com/fcm/send/device");

        var ordered = inOrder(subscriptions, sessions);
        ordered.verify(subscriptions).deleteByEndpointAndUserId("https://fcm.googleapis.com/fcm/send/device", 42L);
        ordered.verify(sessions).deleteById(TokenSecurity.digest("session-token"));
    }

    @Test
    void remainsCompatibleWhenTheBrowserHasNoPushSubscription() {
        AppSessionRepository sessions = mock(AppSessionRepository.class);
        PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.optional()).thenReturn(Optional.empty());
        LogoutService service = new LogoutService(sessions, subscriptions, currentUser);

        service.logout("session-token", null);

        verifyNoInteractions(subscriptions);
        verify(sessions).deleteById(TokenSecurity.digest("session-token"));
    }
}
