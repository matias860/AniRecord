package com.animelist.backend.dto.auth;

public record AuthStatusResponse(boolean authenticated, Long userId, String username, String avatarUrl,
                                 String accountType, boolean needsImport, String lastSyncPromptCommit) {}
