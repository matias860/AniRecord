package com.animelist.backend.controller;

import com.animelist.backend.dto.profile.ProfileResponse;
import com.animelist.backend.dto.profile.UpdateProfileRequest;
import com.animelist.backend.service.ProfileService;
import com.animelist.backend.service.ProfileAssetService;
import com.animelist.backend.enums.ProfileAssetType;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final ProfileService profileService;
    private final ProfileAssetService assetService;
    public ProfileController(ProfileService profileService, ProfileAssetService assetService) {
        this.profileService = profileService;
        this.assetService = assetService;
    }
    @GetMapping public ProfileResponse get() { return profileService.get(); }
    @PatchMapping public ProfileResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return profileService.update(request);
    }
    @PostMapping(value = "/assets/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileResponse upload(@PathVariable String type, @RequestParam("file") MultipartFile file) {
        assetService.save(assetType(type), file);
        return profileService.get();
    }
    @GetMapping("/assets/{type}")
    public ResponseEntity<byte[]> asset(@PathVariable String type) {
        var asset = assetService.get(assetType(type));
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePrivate())
                .contentType(MediaType.parseMediaType(asset.contentType())).body(asset.bytes());
    }
    @DeleteMapping("/assets/{type}")
    public ProfileResponse deleteAsset(@PathVariable String type) {
        assetService.delete(assetType(type));
        return profileService.get();
    }
    private ProfileAssetType assetType(String value) {
        try { return ProfileAssetType.valueOf(value.toUpperCase()); }
        catch (IllegalArgumentException ex) { throw new com.animelist.backend.exception.BusinessException("Tipo de imagen inválido"); }
    }
}
