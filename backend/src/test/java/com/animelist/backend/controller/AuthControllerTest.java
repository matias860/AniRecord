package com.animelist.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.animelist.backend.service.AniListAuthService;
import com.animelist.backend.service.LocalAuthService;
import com.animelist.backend.service.LogoutService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AuthControllerTest {
    @Test
    void bindsOauthLoginToATemporaryBrowserCookie() {
        AniListAuthService auth = mock(AniListAuthService.class);
        when(auth.authorizationRequest()).thenReturn(new AniListAuthService.AuthorizationRequest(
                "https://anilist.co/oauth", "signed-state"));
        AuthController controller = new AuthController(auth, mock(LocalAuthService.class), mock(LogoutService.class), "https://anirecord.com.ar", true);

        var response = controller.login();

        assertEquals("https://anilist.co/oauth", response.getHeaders().getLocation().toString());
        String cookie = response.getHeaders().getFirst("Set-Cookie");
        assertTrue(cookie.contains("anirecord_oauth_state=signed-state"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://anime-list-alca.vercel.app",
            "https://anime-list-matias.vercel.app/",
            "https://anirecord.vercel.app"
    })
    void redirectsLegacyFrontendHostsToCanonicalDomain(String configuredUrl) {
        assertEquals("https://anirecord.com.ar", AuthController.canonicalFrontendUrl(configuredUrl));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:5173",
            "https://preview.example.com/"
    })
    void keepsLocalAndCustomFrontendHosts(String configuredUrl) {
        assertEquals(configuredUrl.replaceAll("/+$", ""), AuthController.canonicalFrontendUrl(configuredUrl));
    }
}
