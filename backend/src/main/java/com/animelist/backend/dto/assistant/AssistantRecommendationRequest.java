package com.animelist.backend.dto.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

public record AssistantRecommendationRequest(
        @NotBlank @Size(max = 400) String text,
        @Valid RecommendationIntent previousIntent
) {}
