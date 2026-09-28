package com.animelist.backend.service;

import com.animelist.backend.entity.AppSession;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.exception.UnauthorizedException;
import com.animelist.backend.repository.AppSessionRepository;
import com.animelist.backend.security.TokenSecurity;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    public static final String COOKIE = "anime_list_session";
    private final AppSessionRepository sessions;
    private final HttpServletRequest request;
    public CurrentUserService(AppSessionRepository sessions, HttpServletRequest request) { this.sessions = sessions; this.request = request; }
    public AppUser require() { return optional().orElseThrow(UnauthorizedException::new); }
    public java.util.Optional<AppUser> optional() {
        if (request.getCookies() == null) return java.util.Optional.empty();
        return Arrays.stream(request.getCookies()).filter(c -> COOKIE.equals(c.getName())).map(Cookie::getValue).findFirst()
                .flatMap(token -> sessions.findByTokenAndExpiresAtAfter(TokenSecurity.digest(token), LocalDateTime.now()))
                .map(AppSession::getUser);
    }
}
