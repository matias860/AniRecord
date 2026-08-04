package com.animelist.backend.service;

import com.animelist.backend.dto.profile.ProfileResponse;
import com.animelist.backend.dto.profile.UpdateProfileRequest;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private static final byte PROFILE_ID = 1;
    private final AppUserRepository repository;

    public ProfileService(AppUserRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ProfileResponse get() {
        return toResponse(repository.findById(PROFILE_ID).orElseGet(() -> repository.save(new AppUser())));
    }

    @Transactional
    public ProfileResponse update(UpdateProfileRequest request) {
        AppUser user = repository.findById(PROFILE_ID).orElseGet(AppUser::new);
        user.setDisplayName(request.displayName().trim());
        user.setBio(request.bio().trim());
        user.setAvatarUrl(normalize(request.avatarUrl()));
        user.setBannerUrl(normalize(request.bannerUrl()));
        user.setBackgroundColor(request.backgroundColor());
        user.setSurfaceColor(request.surfaceColor());
        user.setAccentColor(request.accentColor());
        user.setDotsEnabled(request.dotsEnabled());
        return toResponse(repository.save(user));
    }

    private ProfileResponse toResponse(AppUser user) {
        return new ProfileResponse(user.getDisplayName(), user.getBio(), user.getAvatarUrl(), user.getBannerUrl(),
                user.getBackgroundColor(), user.getSurfaceColor(), user.getAccentColor(), user.getDotsEnabled());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
