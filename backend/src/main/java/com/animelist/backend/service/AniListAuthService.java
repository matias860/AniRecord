package com.animelist.backend.service;

import com.animelist.backend.dto.notification.AniListConnectionResponse;
import com.animelist.backend.entity.AniListConnection;
import com.animelist.backend.exception.BusinessException;
import com.animelist.backend.repository.AniListConnectionRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class AniListAuthService {
    private final AniListConnectionRepository connections;
    private final RestClient restClient = RestClient.create();
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public AniListAuthService(AniListConnectionRepository connections,
            @Value("${anilist.oauth.client-id:}") String clientId,
            @Value("${anilist.oauth.client-secret:}") String clientSecret,
            @Value("${anilist.oauth.redirect-uri:http://localhost:8080/api/anilist/oauth/callback}") String redirectUri) {
        this.connections = connections; this.clientId = clientId; this.clientSecret = clientSecret; this.redirectUri = redirectUri;
    }

    public AniListConnectionResponse status() {
        AniListConnection connection = connections.findById((byte) 1).orElse(null);
        boolean connected = connection != null && (connection.getTokenExpiresAt() == null || connection.getTokenExpiresAt().isAfter(LocalDateTime.now()));
        return new AniListConnectionResponse(connected, connected ? connection.getUsername() : null, connected ? null : authorizationUrl());
    }

    public String authorizationUrl() {
        if (clientId.isBlank()) throw new BusinessException("Falta configurar anilist.oauth.client-id en application-local.properties");
        return "https://anilist.co/api/v2/oauth/authorize?client_id=" + encode(clientId) + "&redirect_uri=" + encode(redirectUri) + "&response_type=code";
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void exchangeCode(String code) {
        if (clientId.isBlank() || clientSecret.isBlank()) throw new BusinessException("Faltan las credenciales OAuth de AniList");
        Map<String, Object> token = restClient.post().uri("https://anilist.co/api/v2/oauth/token")
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .body(Map.of("grant_type", "authorization_code", "client_id", clientId,
                        "client_secret", clientSecret, "redirect_uri", redirectUri, "code", code))
                .retrieve().body(Map.class);
        if (token == null || token.get("access_token") == null) throw new BusinessException("AniList no devolvió un token válido");
        String accessToken = String.valueOf(token.get("access_token"));
        Number expires = (Number) token.getOrDefault("expires_in", 31536000);
        Map<String, Object> viewerBody = restClient.post().uri("https://graphql.anilist.co")
                .header("Authorization", "Bearer " + accessToken).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", "query { Viewer { name } }"))
                .retrieve().body(Map.class);
        Map<String, Object> data = (Map<String, Object>) viewerBody.get("data");
        Map<String, Object> viewer = (Map<String, Object>) data.get("Viewer");
        AniListConnection connection = connections.findById((byte) 1).orElseGet(AniListConnection::new);
        connection.setAccessToken(accessToken);
        connection.setTokenExpiresAt(LocalDateTime.now().plusSeconds(expires.longValue()));
        connection.setUsername(String.valueOf(viewer.get("name")));
        connections.save(connection);
    }

    @Transactional
    public void disconnect() {
        connections.deleteById((byte) 1);
    }

    public String validToken() {
        AniListConnection connection = connections.findById((byte) 1).orElse(null);
        if (connection == null || (connection.getTokenExpiresAt() != null && connection.getTokenExpiresAt().isBefore(LocalDateTime.now()))) return null;
        return connection.getAccessToken();
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
