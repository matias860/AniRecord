package com.animelist.backend.controller;

import com.animelist.backend.dto.anilist.AniListMediaResponse;
import com.animelist.backend.dto.anilist.AniListSearchResponse;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.service.AniListClient;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/anilist")
@Validated
public class AniListController {

    private final AniListClient aniListClient;

    public AniListController(AniListClient aniListClient) {
        this.aniListClient = aniListClient;
    }

    @GetMapping("/search")
    public AniListSearchResponse search(
            @RequestParam @NotBlank @Size(min = 2, max = 100) String query,
            @RequestParam MediaType type,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int perPage
    ) {
        return aniListClient.search(query.trim(), type, page, perPage);
    }

    @GetMapping("/media/{anilistId}")
    public AniListMediaResponse detail(@PathVariable Integer anilistId) {
        return aniListClient.getMedia(anilistId);
    }
}
