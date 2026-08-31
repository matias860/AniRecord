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

@RestController @RequestMapping({"/api/auth", "/api/anilist/oauth"})
public class AuthController {
    private final AniListAuthService auth; private final LocalAuthService localAuth; private final String frontendUrl; private final boolean secure;
    public AuthController(AniListAuthService auth, LocalAuthService localAuth,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
            @Value("${app.cookie-secure:true}") boolean secure){this.auth=auth;this.localAuth=localAuth;this.frontendUrl=frontendUrl.split(",")[0].trim();this.secure=secure;}
    @GetMapping("/status") public AuthStatusResponse status(){return auth.status();}
    @GetMapping({"/login", "/connect"}) public ResponseEntity<Void> login(){return ResponseEntity.status(302).location(URI.create(auth.authorizationUrl())).build();}
    @GetMapping("/callback") public ResponseEntity<Void> callback(@RequestParam(required=false) String code,@RequestParam(required=false) String state,@RequestParam(required=false) String error){
        if(error!=null||code==null||state==null)return redirect("error",null);
        try { var result=auth.exchangeCode(code,state); return redirect("success",result.sessionToken()); }
        catch(RuntimeException ex){return redirect("error",null);}
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
    private ResponseEntity<Void> redirect(String result,String token){String fragment=token==null?"":"&ticket="+token;return ResponseEntity.status(302).location(URI.create(frontendUrl+"/#auth="+result+fragment)).build();}
    private ResponseCookie cookie(String token){return ResponseCookie.from(CurrentUserService.COOKIE,token).httpOnly(true).secure(secure).sameSite(secure?"None":"Lax").path("/").maxAge(java.time.Duration.ofDays(30)).build();}
    private ResponseCookie expired(){return ResponseCookie.from(CurrentUserService.COOKIE,"").httpOnly(true).secure(secure).sameSite(secure?"None":"Lax").path("/").maxAge(0).build();}
    private String cookie(HttpServletRequest r){if(r.getCookies()==null)return null;for(Cookie c:r.getCookies())if(CurrentUserService.COOKIE.equals(c.getName()))return c.getValue();return null;}
}
