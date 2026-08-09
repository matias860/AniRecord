package com.animelist.backend.service;

import static org.mockito.Mockito.*;

import com.animelist.backend.entity.AppUser;
import com.animelist.backend.enums.AccountType;
import com.animelist.backend.enums.ProfileAssetType;
import com.animelist.backend.repository.UserProfileAssetRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ProfileAssetServiceTest {
    @Test void acceptsARealPngForLocalAccounts() {
        UserProfileAssetRepository repository = mock(UserProfileAssetRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        AppUser user = new AppUser(); user.setId(4L); user.setAccountType(AccountType.LOCAL);
        when(currentUser.require()).thenReturn(user);
        when(repository.findByUserIdAndAssetType(4L, ProfileAssetType.AVATAR)).thenReturn(Optional.empty());
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1};
        var file = new MockMultipartFile("file", "avatar.png", "image/png", png);

        new ProfileAssetService(repository, currentUser).save(ProfileAssetType.AVATAR, file);

        verify(repository).save(argThat(asset -> asset.getUser() == user && asset.getData().length == png.length));
    }

    @Test void acceptsUploadsForAniListAccounts() {
        UserProfileAssetRepository repository = mock(UserProfileAssetRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        AppUser user = new AppUser(); user.setId(4L); user.setAccountType(AccountType.ANILIST);
        when(currentUser.require()).thenReturn(user);
        when(repository.findByUserIdAndAssetType(4L, ProfileAssetType.AVATAR)).thenReturn(Optional.empty());
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1};
        var file = new MockMultipartFile("file", "avatar.png", "image/png", png);

        new ProfileAssetService(repository, currentUser).save(ProfileAssetType.AVATAR, file);

        verify(repository).save(argThat(asset -> asset.getUser() == user && asset.getData().length == png.length));
    }
}
