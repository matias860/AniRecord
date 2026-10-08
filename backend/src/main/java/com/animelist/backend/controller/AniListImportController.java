package com.animelist.backend.controller;

import com.animelist.backend.service.AniListImportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/library/import/anilist")
public class AniListImportController {
    public record ApplySelectionRequest(
            @NotNull @Size(max = AniListImportService.BATCH_SIZE) List<Integer> anilistIds) {}

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

    @PostMapping("/{planId}/selection")
    public AniListImportService.BatchResult applySelection(
            @PathVariable UUID planId,
            @Valid @RequestBody ApplySelectionRequest request) {
        return service.applySelection(planId, request.anilistIds());
    }

    @PostMapping("/{planId}/complete")
    public AniListImportService.CompleteResult complete(@PathVariable UUID planId) {
        return service.complete(planId);
    }
}
