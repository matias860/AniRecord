package com.animelist.backend.exception;

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException() { super("Inicia sesión para continuar"); }
    public UnauthorizedException(String message) { super(message); }
}
