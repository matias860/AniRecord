package com.animelist.backend.dto.auth;

import jakarta.validation.constraints.Size;

public record LogoutRequest(@Size(max = 2048) String pushEndpoint) {}
