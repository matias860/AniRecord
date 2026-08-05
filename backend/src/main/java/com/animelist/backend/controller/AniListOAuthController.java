package com.animelist.backend.controller;

import com.animelist.backend.dto.notification.AniListConnectionResponse;
import com.animelist.backend.service.AniListAuthService;
import com.animelist.backend.service.NotificationService;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/anilist/oauth")
public class AniListOAuthController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AniListOAuthController.class);
    private final AniListAuthService auth;
    private final NotificationService notifications;
    private final String frontendUrl;
    public AniListOAuthController(AniListAuthService auth, NotificationService notifications,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.auth = auth; this.notifications = notifications; this.frontendUrl = frontendUrl;
    }
    @GetMapping("/status") public AniListConnectionResponse status() { return auth.status(); }
    @GetMapping("/connect") public ResponseEntity<Void> connect() { return ResponseEntity.status(302).location(URI.create(auth.authorizationUrl())).build(); }
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        if (error != null || code == null || state == null) return redirect("error");
        try {
            auth.exchangeCode(code, state);
            notifications.initialSync();
            return redirect("connected");
        } catch (RuntimeException exception) {
            LOGGER.warn("AniList no pudo completar el callback OAuth", exception);
            return redirect("error");
        }
    }
    @DeleteMapping public void disconnect() { auth.disconnect(); }
    private ResponseEntity<Void> redirect(String result) { return ResponseEntity.status(302).location(URI.create(frontendUrl + "/perfil?anilist=" + result)).build(); }
}
