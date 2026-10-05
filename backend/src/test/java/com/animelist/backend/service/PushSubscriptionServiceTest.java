package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.animelist.backend.dto.push.PushSubscriptionRequest;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.exception.BusinessException;
import com.animelist.backend.repository.PushSubscriptionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTest {
    @Mock PushSubscriptionRepository subscriptions;
    @Mock CurrentUserService currentUser;
    @Mock HttpServletRequest request;
    PushSubscriptionService service;

    @BeforeEach void setUp() {
        service = new PushSubscriptionService(subscriptions, currentUser, request, "public-key");
    }

    @Test void rejectsArbitraryHttpsEndpointsToPreventServerSideRequests() {
        PushSubscriptionRequest input = request("https://example.com/private");
        assertThrows(BusinessException.class, () -> service.save(input));
        verifyNoInteractions(subscriptions, currentUser);
    }

    @Test void acceptsKnownBrowserPushServices() {
        AppUser user = new AppUser(); user.setId(7L);
        when(currentUser.require()).thenReturn(user);
        when(subscriptions.findByEndpoint(anyString())).thenReturn(Optional.empty());
        var response = service.save(request("https://fcm.googleapis.com/fcm/send/example"));
        assertTrue(response.enabled());
        verify(subscriptions).save(argThat(value -> value.getUser() == user && value.isEpisodesEnabled()));
    }

    private PushSubscriptionRequest request(String endpoint) {
        return new PushSubscriptionRequest(endpoint,
                new PushSubscriptionRequest.Keys("BEl6Y2hlY2sta2V5", "YXV0aC1rZXk"), true, true);
    }
}
