package com.animelist.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OAuthClaimRequest(
        @NotBlank
        @Pattern(regexp = "^[0-9a-f]{64}$", message = "El acceso temporal no es válido")
        String ticket
) {}
