package com.animelist.backend.dto.push;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PushSubscriptionRequest(
        @NotBlank @Size(max = 2048) String endpoint,
        @NotNull @Valid Keys keys,
        boolean premieresEnabled,
        boolean episodesEnabled
) {
    public record Keys(
            @NotBlank @Size(max = 180) String p256dh,
            @NotBlank @Size(max = 80) String auth
    ) {}
}
