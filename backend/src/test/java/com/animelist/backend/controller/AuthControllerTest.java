package com.animelist.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AuthControllerTest {
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
