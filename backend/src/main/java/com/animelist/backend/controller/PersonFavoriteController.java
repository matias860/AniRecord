package com.animelist.backend.controller;

import com.animelist.backend.dto.favorite.FavoritePersonRequest;
import com.animelist.backend.dto.favorite.FavoritePersonResponse;
import com.animelist.backend.enums.PersonType;
import com.animelist.backend.service.PersonFavoriteService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/favorites/people")
public class PersonFavoriteController {
    private final PersonFavoriteService service;
    public PersonFavoriteController(PersonFavoriteService service) { this.service = service; }
    @GetMapping public List<FavoritePersonResponse> list() { return service.list(); }
    @PostMapping public FavoritePersonResponse add(@Valid @RequestBody FavoritePersonRequest request) { return service.add(request); }
    @DeleteMapping("/{type}/{anilistId}") public ResponseEntity<Void> remove(@PathVariable PersonType type, @PathVariable Integer anilistId) { service.remove(anilistId, type); return ResponseEntity.noContent().build(); }
}
