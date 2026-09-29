package com.animelist.backend.controller;

import com.animelist.backend.service.AniListExportService;
import com.animelist.backend.enums.MediaType;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/library/export/anilist")
public class AniListExportController {
    private final AniListExportService service;
    public AniListExportController(AniListExportService service) { this.service = service; }

    @PostMapping("/preview")
    public AniListExportService.Preview preview(@RequestParam(defaultValue = "ANIME") MediaType type) { return service.preview(type); }

    @PostMapping("/{planId}/entries/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apply(@PathVariable UUID planId, @PathVariable Long entryId) { service.apply(planId, entryId); }
}
