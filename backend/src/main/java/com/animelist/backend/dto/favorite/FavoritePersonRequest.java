package com.animelist.backend.dto.favorite;

import com.animelist.backend.enums.PersonType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record FavoritePersonRequest(
        @NotNull @Positive Integer anilistId,
        @NotNull PersonType personType,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String nativeName,
        @Size(max = 1000) String imageUrl
) {}
