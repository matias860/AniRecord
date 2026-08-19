package com.animelist.backend.dto.anilist;

import java.util.List;

public record MediaCreditResponse(
        String creditType,
        Integer anilistId,
        String name,
        String image,
        List<String> roles
) {
}
