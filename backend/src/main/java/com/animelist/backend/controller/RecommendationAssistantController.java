package com.animelist.backend.controller;

import com.animelist.backend.dto.assistant.AssistantRecommendationRequest;
import com.animelist.backend.dto.assistant.AssistantRecommendationResponse;
import com.animelist.backend.service.RecommendationAssistantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class RecommendationAssistantController {
    private final RecommendationAssistantService assistant;
    public RecommendationAssistantController(RecommendationAssistantService assistant) { this.assistant = assistant; }

    @PostMapping("/recommend")
    public AssistantRecommendationResponse recommend(@Valid @RequestBody AssistantRecommendationRequest request) {
        return assistant.recommend(request.text(), request.previousIntent());
    }
}
