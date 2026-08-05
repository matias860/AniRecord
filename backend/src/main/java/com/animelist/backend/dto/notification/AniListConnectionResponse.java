package com.animelist.backend.dto.notification;

public record AniListConnectionResponse(boolean connected, boolean configured, String username, String authorizationUrl) {}
