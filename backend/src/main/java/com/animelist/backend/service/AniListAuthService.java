package com.animelist.backend.service;

import com.animelist.backend.dto.auth.AuthStatusResponse;
import com.animelist.backend.entity.*;
import com.animelist.backend.exception.BusinessException;
import com.animelist.backend.enums.AccountType;
import com.animelist.backend.repository.*;
import com.animelist.backend.security.SecretCipher;
import com.animelist.backend.security.OAuthStateToken;
import com.animelist.backend.security.TokenSecurity;
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
    private final AppUserRepository users;
    private final AppSessionRepository sessions;
    private final CurrentUserService currentUser;
    private final RestClient client = RestClient.create();
    private final String clientId, clientSecret, redirectUri;
    private final SecretCipher tokenCipher;
    private final OAuthStateToken oauthStateToken;

    public AniListAuthService(AniListConnectionRepository connections, AppUserRepository users,
            AppSessionRepository sessions, CurrentUserService currentUser,
            @Value("${anilist.oauth.client-id:}") String clientId,
            @Value("${anilist.oauth.client-secret:}") String clientSecret,
            @Value("${anilist.oauth.redirect-uri:http://localhost:8080/api/auth/callback}") String redirectUri,
            @Value("${app.token-encryption-key:}") String tokenEncryptionKey) {
        this.connections=connections; this.users=users; this.sessions=sessions; this.currentUser=currentUser;
        this.clientId=clientId; this.clientSecret=clientSecret; this.redirectUri=redirectUri;
        String encryptionSecret = tokenEncryptionKey.isBlank() ? clientSecret : tokenEncryptionKey;
        this.tokenCipher = new SecretCipher(encryptionSecret.isBlank() ? "local-development-only" : encryptionSecret);
        this.oauthStateToken = new OAuthStateToken(clientSecret.isBlank() ? "local-development-oauth-state" : clientSecret);
    }

    public AuthStatusResponse status() { return currentUser.optional()
            .map(u -> new AuthStatusResponse(true, u.getId(), u.getAccountType() == AccountType.ANILIST ? u.getAnilistUsername() : u.getLocalUsername(),
                    u.getAvatarUrl(), u.getAccountType().name(), u.getAccountType() == AccountType.ANILIST && u.getLastAniListSyncAt()==null,
                    u.getLastSyncPromptCommit()))
            .orElseGet(() -> new AuthStatusResponse(false, null, null, null, null, false, null)); }

    @Transactional
    public String acknowledgeSyncPrompt(String commit) {
        AppUser user = currentUser.require();
        user.setLastSyncPromptCommit(commit);
        users.save(user);
        return commit;
    }

    public String authorizationUrl() {
        if (clientId.isBlank() || clientSecret.isBlank()) throw new BusinessException("Faltan las credenciales OAuth de AniList");
        String state=oauthStateToken.create();
        return "https://anilist.co/api/v2/oauth/authorize?client_id="+enc(clientId)+"&redirect_uri="+enc(redirectUri)+"&response_type=code&state="+enc(state);
    }

    @Transactional @SuppressWarnings("unchecked")
    public LoginResult exchangeCode(String code, String state) {
        if (!oauthStateToken.isValid(state)) throw new BusinessException("La autorización venció. Vuelve a iniciar sesión.");
        Map<String,Object> token=client.post().uri("https://anilist.co/api/v2/oauth/token").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("grant_type","authorization_code","client_id",clientId,"client_secret",clientSecret,"redirect_uri",redirectUri,"code",code))
                .retrieve().body(Map.class);
        if (token==null || token.get("access_token")==null) throw new BusinessException("AniList no devolvió un token válido");
        String accessToken=String.valueOf(token.get("access_token"));
        Map<String,Object> viewer=fetchViewer(accessToken);
        Integer externalId=((Number)viewer.get("id")).intValue(); String name=String.valueOf(viewer.get("name"));
        AppUser user=users.findByAnilistUserId(externalId).orElseGet(AppUser::new);
        user.setAccountType(AccountType.ANILIST); user.setAnilistUserId(externalId); user.setAnilistUsername(name); user.setDisplayName(name); updateImages(user, viewer); user=users.save(user);
        AniListConnection connection=connections.findById(user.getId()).orElseGet(AniListConnection::new);
        connection.setUser(user); connection.setAccessToken(tokenCipher.encrypt(accessToken));
        Number seconds=(Number)token.getOrDefault("expires_in",31536000); connection.setTokenExpiresAt(LocalDateTime.now().plusSeconds(seconds.longValue())); connections.save(connection);
        sessions.deleteByExpiresAtBefore(LocalDateTime.now());
        String rawSession = TokenSecurity.randomToken();
        AppSession session=new AppSession(); session.setToken(TokenSecurity.digest(rawSession)); session.setUser(user); session.setExpiresAt(LocalDateTime.now().plusDays(30)); sessions.save(session);
        return new LoginResult(rawSession, user.getId(), user.getAnilistUsername(), user.getLastAniListSyncAt()==null);
    }

    @Transactional public String validToken() { return validToken(currentUser.require()); }
    @Transactional public String validToken(AppUser user) {
        return connections.findById(user.getId())
                .filter(connection -> connection.getTokenExpiresAt()==null || connection.getTokenExpiresAt().isAfter(LocalDateTime.now()))
                .map(connection -> {
                    String stored = connection.getAccessToken();
                    String plain = tokenCipher.decrypt(stored);
                    if (!tokenCipher.isEncrypted(stored)) {
                        connection.setAccessToken(tokenCipher.encrypt(plain));
                        connections.save(connection);
                    }
                    return plain;
                }).orElse(null);
    }
    @Transactional public void logout(String sessionToken) {
        if (sessionToken==null) return;
        sessions.deleteById(TokenSecurity.digest(sessionToken));
        if (sessionToken.length() <= 64) sessions.deleteById(sessionToken);
    }
    @Transactional public String claim(String ticket) {
        AppSession pendingSession=sessions.findByTokenAndExpiresAtAfter(TokenSecurity.digest(ticket), LocalDateTime.now())
                .or(() -> sessions.findByTokenAndExpiresAtAfter(ticket, LocalDateTime.now()))
                .orElseThrow(() -> new BusinessException("El acceso venció"));
        sessions.delete(pendingSession);
        String rawSession=TokenSecurity.randomToken();
        AppSession session=new AppSession(); session.setToken(TokenSecurity.digest(rawSession)); session.setUser(pendingSession.getUser()); session.setExpiresAt(LocalDateTime.now().plusDays(30)); sessions.save(session);
        return rawSession;
    }
    @Transactional public void refreshProfile() { AppUser user=currentUser.require(); updateImages(user, fetchViewer(validToken(user))); users.save(user); }

    @SuppressWarnings("unchecked") private Map<String,Object> fetchViewer(String token) {
        Map<String,Object> body=client.post().uri("https://graphql.anilist.co").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query","query { Viewer { id name avatar { large } bannerImage } }")).retrieve().body(Map.class);
        if (body==null || !(body.get("data") instanceof Map<?,?> data) || !(data.get("Viewer") instanceof Map)) throw new BusinessException("AniList no devolvió el perfil");
        return (Map<String,Object>)data.get("Viewer");
    }
    @SuppressWarnings("unchecked") private void updateImages(AppUser user, Map<String,Object> viewer) {
        Map<String,Object> avatar=viewer.get("avatar") instanceof Map ? (Map<String,Object>)viewer.get("avatar") : Map.of();
        if (avatar.get("large")!=null) user.setAvatarUrl(String.valueOf(avatar.get("large")));
        if (viewer.get("bannerImage")!=null) user.setBannerUrl(String.valueOf(viewer.get("bannerImage")));
    }
    private String enc(String value){return URLEncoder.encode(value, StandardCharsets.UTF_8);}
    public record LoginResult(String sessionToken, Long userId, String username, boolean firstLogin) {}
}
