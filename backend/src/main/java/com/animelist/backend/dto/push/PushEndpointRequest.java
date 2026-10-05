package com.animelist.backend.dto.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushEndpointRequest(@NotBlank @Size(max = 2048) String endpoint) {}
