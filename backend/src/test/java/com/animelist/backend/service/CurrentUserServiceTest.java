package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.animelist.backend.entity.AppSession;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.repository.AppSessionRepository;
import com.animelist.backend.security.TokenSecurity;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CurrentUserServiceTest {
    @Test void resolvesNewHashedSessions() {
        String raw = TokenSecurity.randomToken();
        AppUser user = new AppUser(); user.setId(3L);
        AppSession session = new AppSession(); session.setUser(user);
        AppSessionRepository sessions = mock(AppSessionRepository.class);
        when(sessions.findByTokenAndExpiresAtAfter(eq(TokenSecurity.digest(raw)), any(LocalDateTime.class)))
                .thenReturn(Optional.of(session));
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CurrentUserService.COOKIE, raw));

        assertEquals(3L, new CurrentUserService(sessions, request).require().getId());
    }

    @Test void keepsLegacySessionsWorkingUntilTheyExpire() {
        String legacy = "legacy-plain-session";
        AppUser user = new AppUser(); user.setId(4L);
        AppSession session = new AppSession(); session.setUser(user);
        AppSessionRepository sessions = mock(AppSessionRepository.class);
        when(sessions.findByTokenAndExpiresAtAfter(eq(TokenSecurity.digest(legacy)), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(sessions.findByTokenAndExpiresAtAfter(eq(legacy), any(LocalDateTime.class)))
                .thenReturn(Optional.of(session));
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CurrentUserService.COOKIE, legacy));

        assertEquals(4L, new CurrentUserService(sessions, request).require().getId());
    }
}
