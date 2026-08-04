package com.animelist.backend.exception;

import java.time.LocalDateTime;
import java.util.Map;

public record ApiErrorResponse(
        LocalDateTime timestamp,
        int status,
        String message,
        Map<String, String> validationErrors
) {
}
