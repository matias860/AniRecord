package com.animelist.backend.controller;

import com.animelist.backend.service.AniListImportService;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/library/import/anilist")
public class AniListImportController {
    private final AniListImportService service;

    public AniListImportController(AniListImportService service) {
        this.service = service;
    }

    @PostMapping("/preview")
    public AniListImportService.Preview preview() {
        return service.preview();
    }

    @PostMapping("/{planId}/batches/{batchIndex}")
    public AniListImportService.BatchResult applyBatch(
            @PathVariable UUID planId, @PathVariable int batchIndex) {
        return service.applyBatch(planId, batchIndex);
    }

    @PostMapping("/{planId}/complete")
    public AniListImportService.CompleteResult complete(@PathVariable UUID planId) {
        return service.complete(planId);
    }
}
