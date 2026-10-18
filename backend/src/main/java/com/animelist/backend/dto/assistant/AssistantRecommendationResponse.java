package com.animelist.backend.dto.assistant;

import java.util.List;

public record AssistantRecommendationResponse(
        String source,
        String reply,
        RecommendationIntent intent,
        List<AssistantMediaResponse> results,
        int remainingAiRequests,
        List<String> understood
) {}
