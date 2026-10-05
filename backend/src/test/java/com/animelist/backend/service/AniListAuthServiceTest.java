package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.animelist.backend.entity.AppUser;
import com.animelist.backend.repository.AniListConnectionRepository;
import com.animelist.backend.repository.AppSessionRepository;
import com.animelist.backend.repository.AppUserRepository;
import com.animelist.backend.repository.OAuthLoginTicketRepository;
import com.animelist.backend.entity.OAuthLoginTicket;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import org.junit.jupiter.api.Test;

class AniListAuthServiceTest {
    @Test
    void refusesAProductionOauthConfigurationWithoutSecrets() {
        assertThrows(IllegalStateException.class, () -> new AniListAuthService(
                mock(AniListConnectionRepository.class),
                mock(AppUserRepository.class),
                mock(AppSessionRepository.class),
                mock(CurrentUserService.class),
                mock(OAuthLoginTicketRepository.class),
                "client-id", "", "https://anirecord.com.ar/api/auth/callback", ""
        ));
    }

    @Test
    void storesTheAcknowledgedCommitOnTheCurrentAccount() {
        AppUserRepository users = mock(AppUserRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        AppUser user = new AppUser();
        user.setId(7L);
        when(currentUser.require()).thenReturn(user);
        AniListAuthService service = new AniListAuthService(
                mock(AniListConnectionRepository.class),
                users,
                mock(AppSessionRepository.class),
                currentUser,
                mock(OAuthLoginTicketRepository.class),
                "", "", "", "test-encryption-key"
        );

        assertEquals("abc1234", service.acknowledgeSyncPrompt("abc1234"));
        assertEquals("abc1234", user.getLastSyncPromptCommit());
        verify(users).save(user);
    }

    @Test
    void exchangesAOneTimeTicketForARealSession() {
        AppSessionRepository sessions = mock(AppSessionRepository.class);
        OAuthLoginTicketRepository tickets = mock(OAuthLoginTicketRepository.class);
        AppUser user = new AppUser();
        user.setId(8L);
        OAuthLoginTicket ticket = new OAuthLoginTicket();
        ticket.setToken("digest");
        ticket.setUser(user);
        ticket.setExpiresAt(LocalDateTime.now().plusMinutes(1));
        when(tickets.findByTokenAndExpiresAtAfter(any(), any())).thenReturn(Optional.of(ticket));
        AniListAuthService service = new AniListAuthService(
                mock(AniListConnectionRepository.class), mock(AppUserRepository.class), sessions,
                mock(CurrentUserService.class), tickets, "", "", "", "test-encryption-key");

        String session = service.claim("temporary-ticket");

        verify(tickets).delete(ticket);
        verify(sessions).save(argThat(saved -> saved.getUser() == user
                && saved.getToken().equals(com.animelist.backend.security.TokenSecurity.digest(session))));
    }
}
