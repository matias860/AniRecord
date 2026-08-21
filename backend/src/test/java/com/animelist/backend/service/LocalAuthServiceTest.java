package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.animelist.backend.dto.auth.LocalRegisterRequest;
import com.animelist.backend.entity.AppSession;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.enums.AccountType;
import com.animelist.backend.repository.AppSessionRepository;
import com.animelist.backend.repository.AppUserRepository;
import com.animelist.backend.security.TokenSecurity;
import org.junit.jupiter.api.Test;

class LocalAuthServiceTest {
    @Test void createsAnIndependentLocalAccountWithHashedPassword() {
        AppUserRepository users = mock(AppUserRepository.class);
        AppSessionRepository sessions = mock(AppSessionRepository.class);
        when(users.existsByLocalUsernameIgnoreCase("alca")).thenReturn(false);
        when(users.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0); user.setId(9L); return user;
        });
        when(sessions.save(any(AppSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        LocalAuthService service = new LocalAuthService(users, sessions);

        var result = service.register(new LocalRegisterRequest("AlCa", "Alca", "contraseña-segura"));

        assertEquals(AccountType.LOCAL.name(), result.status().accountType());
        assertEquals("alca", result.status().username());
        assertFalse(result.status().needsImport());
        verify(users).save(argThat(user -> user.getAccountType() == AccountType.LOCAL
                && user.getAnilistUserId() == null && !user.getPasswordHash().equals("contraseña-segura")));
        verify(sessions).save(argThat(session -> session.getToken().equals(TokenSecurity.digest(result.sessionToken()))
                && !session.getToken().equals(result.sessionToken())));
    }
}
