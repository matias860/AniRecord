package com.animelist.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Limit limit = limitFor(request);
        if (limit != null && !allow(clientKey(request) + ':' + limit.group(), limit)) {
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
        if (path.startsWith("/api/anilist/") && !path.contains("/oauth/")) return new Limit("anilist", 120, 60);
        return null;
    }

    private boolean allow(String key, Limit limit) {
        long now = Instant.now().getEpochSecond();
        Window window = windows.compute(key, (ignored, previous) -> {
            if (previous == null || now - previous.startedAt() >= limit.seconds()) return new Window(now, 1);
            return new Window(previous.startedAt(), previous.count() + 1);
        });
        if (windows.size() > 10_000) windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() > 900);
        return window.count() <= limit.requests();
    }

    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }

    private record Limit(String group, int requests, int seconds) {}
    private record Window(long startedAt, int count) {}
}
