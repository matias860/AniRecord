package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.animelist.backend.entity.AppUser;
import com.animelist.backend.repository.AniListConnectionRepository;
import com.animelist.backend.repository.AppSessionRepository;
import com.animelist.backend.repository.AppUserRepository;
import org.junit.jupiter.api.Test;

class AniListAuthServiceTest {
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
                "", "", "", "test-encryption-key"
        );

        assertEquals("abc1234", service.acknowledgeSyncPrompt("abc1234"));
        assertEquals("abc1234", user.getLastSyncPromptCommit());
        verify(users).save(user);
    }
}
