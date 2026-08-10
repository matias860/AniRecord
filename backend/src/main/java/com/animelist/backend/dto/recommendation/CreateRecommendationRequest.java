package com.animelist.backend.dto.recommendation;

import jakarta.validation.constraints.NotNull;

public record CreateRecommendationRequest(
        @NotNull Long libraryEntryId
) {
}
