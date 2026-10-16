package com.animelist.backend.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GlobalExceptionHandlerTest {
    @Test
    void identifiesAniListGatewayFailuresForTheFrontend() {
        var response = new GlobalExceptionHandler().handleAniList(
                new AniListClientException("AniList no respondió"));

        assertEquals(502, response.getStatusCode().value());
        assertEquals("anilist", response.getBody().validationErrors().get("service"));
    }
}
