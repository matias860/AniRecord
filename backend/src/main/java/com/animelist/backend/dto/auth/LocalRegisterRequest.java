package com.animelist.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LocalRegisterRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{3,30}$", message = "Usá entre 3 y 30 letras, números, puntos, guiones o guiones bajos") String username,
        @NotBlank @Size(max = 40) String displayName,
        @NotBlank @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres") String password
) {}
