package com.animelist.backend.controller;

import com.animelist.backend.dto.anilist.AiringMediaResponse;
import com.animelist.backend.dto.library.AddLibraryEntryRequest;
import com.animelist.backend.dto.library.LibraryEntryResponse;
import com.animelist.backend.dto.library.LibraryImportResponse;
import com.animelist.backend.dto.library.UpdateLibraryEntryRequest;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.TrackingStatus;
import com.animelist.backend.service.LibraryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/library")
public class LibraryController {

    private final LibraryService libraryService;

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @GetMapping
    public List<LibraryEntryResponse> findAll(
            @RequestParam(required = false) MediaType type,
            @RequestParam(required = false) TrackingStatus status
    ) {
        return libraryService.findAll(type, status);
    }

    @GetMapping("/{id}")
    public LibraryEntryResponse findById(@PathVariable Long id) {
        return libraryService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LibraryEntryResponse add(@Valid @RequestBody AddLibraryEntryRequest request) {
        return libraryService.add(request);
    }

    @PostMapping("/import/anilist")
    public LibraryImportResponse importFromAniList(
            @RequestParam @NotBlank @Size(max = 50) String username
    ) {
        return libraryService.importFromAniList(username);
    }

    @PatchMapping("/{id}")
    public LibraryEntryResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLibraryEntryRequest request
    ) {
        return libraryService.update(id, request);
    }

    @PostMapping("/{id}/progress/increment")
    public LibraryEntryResponse incrementProgress(@PathVariable Long id) {
        return libraryService.incrementProgress(id);
    }

    @PostMapping("/{id}/progress/decrement")
    public LibraryEntryResponse decrementProgress(@PathVariable Long id) {
        return libraryService.decrementProgress(id);
    }

    @PostMapping("/{id}/sync")
    public LibraryEntryResponse synchronize(@PathVariable Long id) {
        return libraryService.synchronize(id);
    }

    @GetMapping("/airing")
    public List<AiringMediaResponse> upcomingEpisodes() {
        return libraryService.findUpcomingEpisodes();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        libraryService.delete(id);
    }
}
