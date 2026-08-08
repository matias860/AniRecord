package com.animelist.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LocalLoginRequest(@NotBlank @Size(max = 30) String username, @NotBlank @Size(max = 72) String password) {}
