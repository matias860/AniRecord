package com.animelist.backend.dto.notification;

public record AniListConnectionResponse(boolean connected, String username, String authorizationUrl) {}
