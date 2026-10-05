package com.animelist.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.animelist.backend.service.CurrentUserService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.time.Instant;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_WINDOWS = 20_000;
    private final Cache<String, Window> windows = Caffeine.newBuilder()
            .maximumSize(MAX_WINDOWS)
            .expireAfterAccess(Duration.ofHours(1))
            .build();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Limit limit = limitFor(request);
        if (limit != null && !allowed(request, limit)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(limit.seconds()));
            response.getWriter().write("{\"status\":429,\"message\":\"Demasiadas solicitudes. Intenta nuevamente en unos minutos.\",\"errors\":{}}");
            return;
        }
        chain.doFilter(request, response);
    }

    private Limit limitFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.matches("/api/(auth|anilist/oauth)/(local-login|register|claim|login|connect)")) return new Limit("auth", 12, 600);
        if ("POST".equals(request.getMethod()) && path.equals("/api/library/import/anilist")) return new Limit("library-import", 4, 3600);
        if ("POST".equals(request.getMethod()) && path.matches("/api/library/\\d+/sync")) return new Limit("library-sync", 60, 600);
        if ("POST".equals(request.getMethod()) && path.matches("/api/profile/assets/(avatar|banner)")) return new Limit("profile-upload", 12, 3600);
        if ("POST".equals(request.getMethod()) && path.equals("/api/top-nine")) return new Limit("top-nine", 20, 3600);
        if ("POST".equals(request.getMethod()) && path.equals("/api/push/test")) return new Limit("push-test", 5, 3600);
        if ("POST".equals(request.getMethod()) && path.equals("/api/recommendations")) return new Limit("recommendations", 20, 86400);
        if (path.startsWith("/api/anilist/") && !path.contains("/oauth/")) return new Limit("anilist", 120, 60);
        if (path.startsWith("/api/") && !isSafe(request.getMethod())) return new Limit("api-write", 300, 60);
        return null;
    }

    private boolean allowed(HttpServletRequest request, Limit limit) {
        String ip = clientAddress(request);
        Limit proxyLimit = new Limit(limit.group(), Math.max(limit.requests() * 100, 1_000), limit.seconds());
        if (!allow("proxy:" + request.getRemoteAddr() + ':' + limit.group(), proxyLimit)) return false;
        Optional<String> session = sessionKey(request);
        if (!allow("ip:" + ip + ':' + limit.group(), limit)) return false;
        return session.map(value -> allow("session:" + value + ':' + limit.group(), limit)).orElse(true);
    }

    private boolean allow(String key, Limit limit) {
        long now = Instant.now().getEpochSecond();
        Window window = windows.asMap().compute(key, (ignored, previous) -> {
            if (previous == null || now >= previous.expiresAt()) return new Window(1, now + limit.seconds());
            return new Window(previous.count() + 1, previous.expiresAt());
        });
        return window.count() <= limit.requests();
    }

    private String clientAddress(HttpServletRequest request) {
        if (request.getHeader("X-Vercel-Id") == null) return request.getRemoteAddr();
        String forwarded = request.getHeader("X-Vercel-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank() || forwarded.length() > 512) return request.getRemoteAddr();
        String client = forwarded.split(",", 2)[0].trim();
        return validAddress(client) ? client : request.getRemoteAddr();
    }

    private boolean validAddress(String value) {
        return !value.isBlank() && value.length() <= 64 && value.matches("[0-9a-fA-F:.]+");
    }

    private Optional<String> sessionKey(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies())
                .filter(cookie -> CurrentUserService.COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value.length() == 64)
                .findFirst()
                .map(TokenSecurity::digest);
    }

    private boolean isSafe(String method) {
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }

    private record Limit(String group, int requests, int seconds) {}
    private record Window(int count, long expiresAt) {}
}
