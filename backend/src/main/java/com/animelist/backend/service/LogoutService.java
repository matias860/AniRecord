package com.animelist.backend.service;

import com.animelist.backend.repository.AppSessionRepository;
import com.animelist.backend.repository.PushSubscriptionRepository;
import com.animelist.backend.security.TokenSecurity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutService {
    private final AppSessionRepository sessions;
    private final PushSubscriptionRepository subscriptions;
    private final CurrentUserService currentUser;

    public LogoutService(AppSessionRepository sessions, PushSubscriptionRepository subscriptions,
            CurrentUserService currentUser) {
        this.sessions = sessions;
        this.subscriptions = subscriptions;
        this.currentUser = currentUser;
    }

    @Transactional
    public void logout(String sessionToken, String pushEndpoint) {
        currentUser.optional().ifPresent(user -> {
            if (pushEndpoint != null && !pushEndpoint.isBlank()) {
                subscriptions.deleteByEndpointAndUserId(pushEndpoint, user.getId());
            }
        });
        if (sessionToken != null && !sessionToken.isBlank()) {
            sessions.deleteById(TokenSecurity.digest(sessionToken));
        }
    }
}
