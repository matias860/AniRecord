package com.animelist.backend.controller;

import com.animelist.backend.dto.notification.AniListConnectionResponse;
import com.animelist.backend.service.AniListAuthService;
import com.animelist.backend.service.NotificationService;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/anilist/oauth")
public class AniListOAuthController {
    private final AniListAuthService auth;
    private final NotificationService notifications;
    private final String frontendUrl;
    public AniListOAuthController(AniListAuthService auth, NotificationService notifications,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.auth = auth; this.notifications = notifications; this.frontendUrl = frontendUrl;
    }
    @GetMapping("/status") public AniListConnectionResponse status() { return auth.status(); }
    @GetMapping("/connect") public ResponseEntity<Void> connect() { return ResponseEntity.status(302).location(URI.create(auth.authorizationUrl())).build(); }
    @GetMapping("/callback") public ResponseEntity<Void> callback(@RequestParam String code) { auth.exchangeCode(code); notifications.initialSync(); return ResponseEntity.status(302).location(URI.create(frontendUrl + "/perfil?anilist=connected")).build(); }
    @DeleteMapping public void disconnect() { auth.disconnect(); }
}
