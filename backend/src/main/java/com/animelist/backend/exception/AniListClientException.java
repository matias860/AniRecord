package com.animelist.backend.exception;

public class AniListClientException extends RuntimeException {
    public AniListClientException(String message) {
        super(message);
    }

    public AniListClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
