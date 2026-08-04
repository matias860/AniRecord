package com.animelist.backend.dto.library;

public record LibraryImportResponse(
        String username,
        int total,
        int anime,
        int manga,
        int created,
        int updated
) {
}
