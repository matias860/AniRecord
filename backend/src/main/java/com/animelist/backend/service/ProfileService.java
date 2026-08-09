package com.animelist.backend.service;

import com.animelist.backend.dto.profile.ProfileResponse;
import com.animelist.backend.dto.profile.UpdateProfileRequest;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.repository.AppUserRepository;
import com.animelist.backend.repository.UserProfileAssetRepository;
import com.animelist.backend.enums.AccountType;
import com.animelist.backend.enums.ProfileAssetType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final AppUserRepository repository;
    private final CurrentUserService currentUser;
    private final UserProfileAssetRepository assets;

    public ProfileService(AppUserRepository repository, CurrentUserService currentUser, UserProfileAssetRepository assets) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.assets = assets;
    }

    @Transactional
    public ProfileResponse get() {
        return toResponse(currentUser.require());
    }

    @Transactional
    public ProfileResponse update(UpdateProfileRequest request) {
        AppUser user = currentUser.require();
        user.setDisplayName(request.displayName().trim());
        user.setBio(request.bio().trim());
        user.setBackgroundColor(request.backgroundColor());
        user.setSurfaceColor(request.surfaceColor());
        user.setAccentColor(request.accentColor());
        user.setDotsEnabled(request.dotsEnabled());
        return toResponse(repository.save(user));
    }

    private ProfileResponse toResponse(AppUser user) {
        String customAvatar = assetUrl(user.getId(), ProfileAssetType.AVATAR);
        String customBanner = assetUrl(user.getId(), ProfileAssetType.BANNER);
        String avatar = customAvatar == null ? user.getAvatarUrl() : customAvatar;
        String banner = customBanner == null ? user.getBannerUrl() : customBanner;
        return new ProfileResponse(user.getDisplayName(), user.getBio(), avatar, banner,
                user.getBackgroundColor(), user.getSurfaceColor(), user.getAccentColor(), user.getDotsEnabled(),
                user.getAccountType().name(),
                user.getAccountType() == AccountType.ANILIST ? user.getAvatarUrl() : null,
                user.getAccountType() == AccountType.ANILIST ? user.getBannerUrl() : null,
                customAvatar != null, customBanner != null);
    }

    private String assetUrl(Long userId, ProfileAssetType type) {
        return assets.findUpdatedAt(userId, type)
                .map(updated -> "/api/profile/assets/" + type.name().toLowerCase() + "?v=" + updated.toEpochSecond(java.time.ZoneOffset.UTC))
                .orElse(null);
    }

}
