package com.animelist.backend.service;

import com.animelist.backend.dto.notification.AniListConnectionResponse;
import com.animelist.backend.entity.AniListConnection;
import com.animelist.backend.exception.BusinessException;
import com.animelist.backend.repository.AniListConnectionRepository;
import com.animelist.backend.repository.AppUserRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class AniListAuthService {
    private final AniListConnectionRepository connections;
    private final AppUserRepository profiles;
    private final RestClient restClient = RestClient.create();
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final AtomicReference<PendingAuthorization> pendingAuthorization = new AtomicReference<>();

    public AniListAuthService(AniListConnectionRepository connections, AppUserRepository profiles,
            @Value("${anilist.oauth.client-id:}") String clientId,
            @Value("${anilist.oauth.client-secret:}") String clientSecret,
            @Value("${anilist.oauth.redirect-uri:http://localhost:8080/api/anilist/oauth/callback}") String redirectUri) {
        this.connections = connections; this.profiles = profiles; this.clientId = clientId; this.clientSecret = clientSecret; this.redirectUri = redirectUri;
    }

    public AniListConnectionResponse status() {
        AniListConnection connection = connections.findById((byte) 1).orElse(null);
        boolean connected = connection != null && (connection.getTokenExpiresAt() == null || connection.getTokenExpiresAt().isAfter(LocalDateTime.now()));
        boolean configured = !clientId.isBlank() && !clientSecret.isBlank();
        return new AniListConnectionResponse(connected, configured, connected ? connection.getUsername() : null,
                connected || !configured ? null : "/api/anilist/oauth/connect");
    }

    public String authorizationUrl() {
        if (clientId.isBlank()) throw new BusinessException("Falta configurar anilist.oauth.client-id en application-local.properties");
        String state = UUID.randomUUID().toString();
        pendingAuthorization.set(new PendingAuthorization(state, LocalDateTime.now().plusMinutes(10)));
        return "https://anilist.co/api/v2/oauth/authorize?client_id=" + encode(clientId) + "&redirect_uri=" + encode(redirectUri) + "&response_type=code&state=" + encode(state);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void exchangeCode(String code, String state) {
        if (clientId.isBlank() || clientSecret.isBlank()) throw new BusinessException("Faltan las credenciales OAuth de AniList");
        PendingAuthorization pending = pendingAuthorization.getAndSet(null);
        if (pending == null || pending.expiresAt().isBefore(LocalDateTime.now()) || !pending.state().equals(state)) {
            throw new BusinessException("La autorización de AniList no es válida o venció. Volvé a iniciar la conexión.");
        }
        Map<String, Object> token = restClient.post().uri("https://anilist.co/api/v2/oauth/token")
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .body(Map.of("grant_type", "authorization_code", "client_id", clientId,
                        "client_secret", clientSecret, "redirect_uri", redirectUri, "code", code))
                .retrieve().body(Map.class);
        if (token == null || token.get("access_token") == null) throw new BusinessException("AniList no devolvió un token válido");
        String accessToken = String.valueOf(token.get("access_token"));
        Number expires = (Number) token.getOrDefault("expires_in", 31536000);
        Map<String, Object> viewer = fetchViewer(accessToken);
        AniListConnection connection = connections.findById((byte) 1).orElseGet(AniListConnection::new);
        connection.setAccessToken(accessToken);
        connection.setTokenExpiresAt(LocalDateTime.now().plusSeconds(expires.longValue()));
        connection.setUsername(String.valueOf(viewer.get("name")));
        connections.save(connection);
        updateLocalProfile(viewer);
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

    @Transactional
    public void refreshProfile() {
        String token = validToken();
        if (token == null) throw new BusinessException("Conectá tu cuenta de AniList antes de actualizar el perfil");
        updateLocalProfile(fetchViewer(token));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchViewer(String accessToken) {
        Map<String, Object> body = restClient.post().uri("https://graphql.anilist.co")
                .header("Authorization", "Bearer " + accessToken).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", "query { Viewer { name avatar { large } bannerImage } }"))
                .retrieve().body(Map.class);
        if (body == null || !(body.get("data") instanceof Map)) throw new BusinessException("AniList no devolvió los datos del perfil");
        Map<String, Object> viewer = (Map<String, Object>) ((Map<String, Object>) body.get("data")).get("Viewer");
        if (viewer == null) throw new BusinessException("AniList no devolvió los datos del usuario conectado");
        return viewer;
    }

    @SuppressWarnings("unchecked")
    private void updateLocalProfile(Map<String, Object> viewer) {
        var profile = profiles.findById((byte) 1).orElseThrow();
        Map<String, Object> avatar = viewer.get("avatar") instanceof Map ? (Map<String, Object>) viewer.get("avatar") : Map.of();
        profile.setDisplayName(String.valueOf(viewer.get("name")));
        profile.setAvatarUrl(avatar.get("large") == null ? profile.getAvatarUrl() : String.valueOf(avatar.get("large")));
        profile.setBannerUrl(viewer.get("bannerImage") == null ? profile.getBannerUrl() : String.valueOf(viewer.get("bannerImage")));
        profiles.save(profile);
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private record PendingAuthorization(String state, LocalDateTime expiresAt) {}
}
