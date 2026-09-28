package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.animelist.backend.entity.AppSession;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.exception.UnauthorizedException;
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

    @Test void rejectsLegacyPlainTextSessions() {
        String legacy = "legacy-plain-session";
        AppSessionRepository sessions = mock(AppSessionRepository.class);
        when(sessions.findByTokenAndExpiresAtAfter(eq(TokenSecurity.digest(legacy)), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CurrentUserService.COOKIE, legacy));

        assertThrows(UnauthorizedException.class, () -> new CurrentUserService(sessions, request).require());
        verify(sessions, never()).findByTokenAndExpiresAtAfter(eq(legacy), any(LocalDateTime.class));
    }
}
