package com.animelist.backend.controller;

import com.animelist.backend.dto.history.ActivityHistoryResponse;
import com.animelist.backend.service.ActivityHistoryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history")
public class ActivityHistoryController {

    private final ActivityHistoryService service;

    public ActivityHistoryController(ActivityHistoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<ActivityHistoryResponse> findRecent(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return service.findRecent(limit);
    }
}
