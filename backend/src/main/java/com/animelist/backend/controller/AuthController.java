package com.animelist.backend.controller;

import com.animelist.backend.dto.auth.AuthStatusResponse;
import com.animelist.backend.dto.auth.LocalLoginRequest;
import com.animelist.backend.dto.auth.LocalRegisterRequest;
import com.animelist.backend.dto.auth.SyncPromptAcknowledgementRequest;
import com.animelist.backend.service.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController @RequestMapping({"/api/auth", "/api/anilist/oauth"})
public class AuthController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);
    private static final String PUBLIC_FRONTEND_URL = "https://anirecord.com.ar";
    private static final String OAUTH_STATE_COOKIE = "anirecord_oauth_state";
    private final AniListAuthService auth; private final LocalAuthService localAuth; private final String frontendUrl; private final boolean secure;
    public AuthController(AniListAuthService auth, LocalAuthService localAuth,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
            @Value("${app.cookie-secure:true}") boolean secure){this.auth=auth;this.localAuth=localAuth;this.frontendUrl=canonicalFrontendUrl(frontendUrl);this.secure=secure;}
    @GetMapping("/status") public AuthStatusResponse status(){return auth.status();}
    @GetMapping({"/login", "/connect"}) public ResponseEntity<Void> login(){
        var request = auth.authorizationRequest();
        return ResponseEntity.status(302).location(URI.create(request.url()))
                .header(HttpHeaders.SET_COOKIE, oauthStateCookie(request.state()).toString()).build();
    }
    @GetMapping("/callback") public ResponseEntity<Void> callback(@RequestParam(required=false) String code,@RequestParam(required=false) String state,@RequestParam(required=false) String error,HttpServletRequest request){
        if(error!=null||code==null||state==null){
            LOGGER.warn("AniList OAuth callback rejected: providerError={}, missingCode={}, missingState={}", error != null, code == null, state == null);
            return redirectAfterOAuth("error",null);
        }
        try { var result=auth.exchangeCode(code,state,cookie(request,OAUTH_STATE_COOKIE)); return redirectAfterOAuth("success",result.sessionToken()); }
        catch(RuntimeException ex){
            LOGGER.warn("AniList OAuth callback failed: exception={}", ex.getClass().getSimpleName());
            return redirectAfterOAuth("error",null);
        }
    }
    @PostMapping("/claim") public ResponseEntity<Void> claim(@RequestBody Map<String,String> body){String token=body.get("ticket");if(token==null||token.isBlank())return ResponseEntity.badRequest().build();return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,cookie(auth.claim(token)).toString()).build();}
    @PostMapping("/register") public ResponseEntity<AuthStatusResponse> register(@Valid @RequestBody LocalRegisterRequest request) {
        var result = localAuth.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.SET_COOKIE, cookie(result.sessionToken()).toString()).body(result.status());
    }
    @PostMapping("/local-login") public ResponseEntity<AuthStatusResponse> localLogin(@Valid @RequestBody LocalLoginRequest request) {
        var result = localAuth.login(request);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(result.sessionToken()).toString()).body(result.status());
    }
    @PatchMapping("/sync-prompt") public Map<String,String> acknowledgeSyncPrompt(@Valid @RequestBody SyncPromptAcknowledgementRequest request) {
        return Map.of("lastSyncPromptCommit", auth.acknowledgeSyncPrompt(request.commit()));
    }
    @PostMapping("/logout") public ResponseEntity<Void> logout(HttpServletRequest request){String token=cookie(request);auth.logout(token);return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,expired().toString()).build();}
    private ResponseEntity<Void> redirectAfterOAuth(String result,String token){
        String fragment=token==null?"":"&ticket="+token;
        return ResponseEntity.status(302).location(URI.create(frontendUrl+"/#auth="+result+fragment))
                .header(HttpHeaders.SET_COOKIE, expiredOAuthState().toString()).build();
    }
    private ResponseCookie cookie(String token){return ResponseCookie.from(CurrentUserService.COOKIE,token).httpOnly(true).secure(secure).sameSite(secure?"None":"Lax").path("/").maxAge(java.time.Duration.ofDays(30)).build();}
    private ResponseCookie expired(){return ResponseCookie.from(CurrentUserService.COOKIE,"").httpOnly(true).secure(secure).sameSite(secure?"None":"Lax").path("/").maxAge(0).build();}
    private ResponseCookie oauthStateCookie(String state){return ResponseCookie.from(OAUTH_STATE_COOKIE,state).httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(java.time.Duration.ofMinutes(10)).build();}
    private ResponseCookie expiredOAuthState(){return ResponseCookie.from(OAUTH_STATE_COOKIE,"").httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(0).build();}
    private String cookie(HttpServletRequest r){return cookie(r,CurrentUserService.COOKIE);}
    private String cookie(HttpServletRequest r,String name){if(r.getCookies()==null)return null;for(Cookie c:r.getCookies())if(name.equals(c.getName()))return c.getValue();return null;}
    static String canonicalFrontendUrl(String configuredUrls) {
        String configured = configuredUrls.split(",")[0].trim().replaceAll("/+$", "");
        return configured.matches("https://(anime-list-alca|anime-list-matias|anirecord)\\.vercel\\.app")
                ? PUBLIC_FRONTEND_URL
                : configured;
    }
}
