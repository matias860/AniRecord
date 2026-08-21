package com.animelist.backend.service;

import com.animelist.backend.dto.auth.*;
import com.animelist.backend.entity.*;
import com.animelist.backend.enums.AccountType;
import com.animelist.backend.exception.BusinessException;
import com.animelist.backend.exception.UnauthorizedException;
import com.animelist.backend.repository.*;
import com.animelist.backend.security.TokenSecurity;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocalAuthService {
    private final AppUserRepository users;
    private final AppSessionRepository sessions;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public LocalAuthService(AppUserRepository users, AppSessionRepository sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    @Transactional
    public LoginResult register(LocalRegisterRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException("La contraseña es demasiado larga");
        }
        if (users.existsByLocalUsernameIgnoreCase(username)) {
            throw new BusinessException("Ese nombre de usuario ya está en uso");
        }
        AppUser user = new AppUser();
        user.setAccountType(AccountType.LOCAL);
        user.setLocalUsername(username);
        user.setPasswordHash(encoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setAnilistUserId(null);
        user.setAnilistUsername(null);
        user = users.save(user);
        return result(user, newSession(user));
    }

    @Transactional
    public LoginResult login(LocalLoginRequest request) {
        AppUser user = users.findByLocalUsernameIgnoreCase(request.username().trim())
                .filter(candidate -> candidate.getAccountType() == AccountType.LOCAL)
                .orElseThrow(() -> new UnauthorizedException("Usuario o contraseña incorrectos"));
        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Usuario o contraseña incorrectos");
        }
        return result(user, newSession(user));
    }

    private String newSession(AppUser user) {
        sessions.deleteByExpiresAtBefore(LocalDateTime.now());
        String rawToken = TokenSecurity.randomToken();
        AppSession session = new AppSession();
        session.setToken(TokenSecurity.digest(rawToken));
        session.setUser(user);
        session.setExpiresAt(LocalDateTime.now().plusDays(30));
        sessions.save(session);
        return rawToken;
    }

    private LoginResult result(AppUser user, String token) {
        return new LoginResult(token, new AuthStatusResponse(true, user.getId(), user.getLocalUsername(),
                null, AccountType.LOCAL.name(), false));
    }

    public record LoginResult(String sessionToken, AuthStatusResponse status) {}
}
