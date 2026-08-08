package com.animelist.backend.exception;

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException() { super("Iniciá sesión para continuar"); }
    public UnauthorizedException(String message) { super(message); }
}
