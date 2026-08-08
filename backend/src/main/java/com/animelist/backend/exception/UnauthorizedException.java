package com.animelist.backend.exception;

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException() { super("Iniciá sesión con AniList para continuar"); }
    public UnauthorizedException(String message) { super(message); }
}
