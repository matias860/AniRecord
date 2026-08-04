package com.animelist.backend.controller;

import com.animelist.backend.dto.profile.ProfileResponse;
import com.animelist.backend.dto.profile.UpdateProfileRequest;
import com.animelist.backend.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final ProfileService profileService;
    public ProfileController(ProfileService profileService) { this.profileService = profileService; }
    @GetMapping public ProfileResponse get() { return profileService.get(); }
    @PatchMapping public ProfileResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return profileService.update(request);
    }
}
