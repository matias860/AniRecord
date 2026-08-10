package com.animelist.backend.controller;

import com.animelist.backend.dto.recommendation.CreateRecommendationRequest;
import com.animelist.backend.dto.recommendation.WeeklyRecommendationResponse;
import com.animelist.backend.service.WeeklyRecommendationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class WeeklyRecommendationController {
    private final WeeklyRecommendationService service;

    public WeeklyRecommendationController(WeeklyRecommendationService service) {
        this.service = service;
    }

    @GetMapping
    public List<WeeklyRecommendationResponse> currentWeek() {
        return service.currentWeek();
    }

    @GetMapping("/media/{anilistId}")
    public List<WeeklyRecommendationResponse> forMedia(@PathVariable Integer anilistId) {
        return service.forMedia(anilistId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WeeklyRecommendationResponse create(@Valid @RequestBody CreateRecommendationRequest request) {
        return service.create(request);
    }
}
