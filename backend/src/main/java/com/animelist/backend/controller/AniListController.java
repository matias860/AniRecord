package com.animelist.backend.controller;

import com.animelist.backend.dto.anilist.AniListMediaResponse;
import com.animelist.backend.dto.anilist.AniListSearchResponse;
import com.animelist.backend.dto.anilist.CharacterDetailResponse;
import com.animelist.backend.dto.anilist.StaffDetailResponse;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.ReleaseSeason;
import com.animelist.backend.enums.ReleaseStatus;
import com.animelist.backend.service.AniListClient;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
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
            @RequestParam @NotBlank @Size(min = 1, max = 100) String query,
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

    @GetMapping("/catalog")
    public AniListSearchResponse catalog(
            @RequestParam MediaType type,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(30) int perPage,
            @RequestParam(required = false) @Size(min = 1, max = 100) String search,
            @RequestParam(required = false) @Min(1940) @Max(2100) Integer year,
            @RequestParam(required = false) ReleaseSeason season,
            @RequestParam(required = false) List<String> genre,
            @RequestParam(required = false) ReleaseStatus status,
            @RequestParam(required = false)
            @jakarta.validation.constraints.Pattern(regexp = "JP|KR|CN") String country,
            @RequestParam(defaultValue = "POPULARITY")
            @jakarta.validation.constraints.Pattern(regexp = "POPULARITY|SCORE|TRENDING|NEWEST") String sort
    ) {
        return aniListClient.browse(type, page, perPage, search, year, season, genre, status, country, sort);
    }

    @GetMapping("/characters/{anilistId}")
    public CharacterDetailResponse character(@PathVariable Integer anilistId) {
        return aniListClient.getCharacter(anilistId);
    }

    @GetMapping("/staff/{anilistId}")
    public StaffDetailResponse staff(@PathVariable Integer anilistId) {
        return aniListClient.getStaff(anilistId);
    }

    @GetMapping("/catalog/genres")
    public List<String> catalogGenres() {
        return aniListClient.getGenres();
    }

    @GetMapping("/season/current")
    public List<AniListMediaResponse> currentSeason(
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit
    ) {
        return aniListClient.getCurrentSeason(limit);
    }

    @GetMapping("/season")
    public List<AniListMediaResponse> season(
            @RequestParam ReleaseSeason season,
            @RequestParam @Min(1940) @Max(2100) int year,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit
    ) {
        return aniListClient.getSeason(season, year, limit);
    }
}
