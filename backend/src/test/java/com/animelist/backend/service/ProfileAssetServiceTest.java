package com.animelist.backend.service;

import static org.mockito.Mockito.*;

import com.animelist.backend.entity.AppUser;
import com.animelist.backend.enums.AccountType;
import com.animelist.backend.enums.ProfileAssetType;
import com.animelist.backend.repository.UserProfileAssetRepository;
import java.util.Optional;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ProfileAssetServiceTest {
    @Test void publicAssetsDoNotRequireAViewerSession() {
        UserProfileAssetRepository repository = mock(UserProfileAssetRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        var asset = new com.animelist.backend.entity.UserProfileAsset();
        asset.setContentType("image/png");
        asset.setData(new byte[] {1, 2, 3});
        when(repository.findByUserIdAndAssetType(8L, ProfileAssetType.AVATAR)).thenReturn(Optional.of(asset));

        new ProfileAssetService(repository, currentUser).getForUser(8L, ProfileAssetType.AVATAR);

        verify(currentUser, never()).require();
    }

    @Test void acceptsARealPngForLocalAccounts() throws Exception {
        UserProfileAssetRepository repository = mock(UserProfileAssetRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        AppUser user = new AppUser(); user.setId(4L); user.setAccountType(AccountType.LOCAL);
        when(currentUser.require()).thenReturn(user);
        when(repository.findByUserIdAndAssetType(4L, ProfileAssetType.AVATAR)).thenReturn(Optional.empty());
        byte[] png = png();
        var file = new MockMultipartFile("file", "avatar.png", "image/png", png);

        new ProfileAssetService(repository, currentUser).save(ProfileAssetType.AVATAR, file);

        verify(repository).save(argThat(asset -> asset.getUser() == user && asset.getData().length == png.length));
    }

    @Test void acceptsUploadsForAniListAccounts() throws Exception {
        UserProfileAssetRepository repository = mock(UserProfileAssetRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        AppUser user = new AppUser(); user.setId(4L); user.setAccountType(AccountType.ANILIST);
        when(currentUser.require()).thenReturn(user);
        when(repository.findByUserIdAndAssetType(4L, ProfileAssetType.AVATAR)).thenReturn(Optional.empty());
        byte[] png = png();
        var file = new MockMultipartFile("file", "avatar.png", "image/png", png);

        new ProfileAssetService(repository, currentUser).save(ProfileAssetType.AVATAR, file);

        verify(repository).save(argThat(asset -> asset.getUser() == user && asset.getData().length == png.length));
    }

    @Test void rejectsTruncatedImagesThatOnlyHaveAPngSignature() {
        UserProfileAssetRepository repository = mock(UserProfileAssetRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        AppUser user = new AppUser(); user.setId(4L);
        when(currentUser.require()).thenReturn(user);
        byte[] fake = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1};

        assertThrows(com.animelist.backend.exception.BusinessException.class,
                () -> new ProfileAssetService(repository, currentUser).save(ProfileAssetType.AVATAR,
                        new MockMultipartFile("file", "avatar.png", "image/png", fake)));
        verify(repository, never()).save(any());
    }

    @Test void acceptsAWebPThatCanBeDecoded() {
        UserProfileAssetRepository repository = mock(UserProfileAssetRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        AppUser user = new AppUser(); user.setId(4L);
        when(currentUser.require()).thenReturn(user);
        when(repository.findByUserIdAndAssetType(4L, ProfileAssetType.AVATAR)).thenReturn(Optional.empty());
        byte[] webp = Base64.getDecoder().decode("UklGRlYAAABXRUJQVlA4IDoAAADwAgCdASoBAAEAAEcIhYWIhYSIAgICdaoD+AP6Ag1NGAD+/vNYf/5gZt2KO//mBv/80F4SW6//zLwASUNNVAgAAAB0ZXN0MXgxAA==");

        new ProfileAssetService(repository, currentUser).save(ProfileAssetType.AVATAR,
                new MockMultipartFile("file", "avatar.webp", "image/webp", webp));

        verify(repository).save(argThat(asset -> asset.getData().length == webp.length));
    }

    @Test void rejectsAWebPWithAValidContainerButCorruptFrame() {
        UserProfileAssetRepository repository = mock(UserProfileAssetRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        AppUser user = new AppUser(); user.setId(4L);
        when(currentUser.require()).thenReturn(user);
        byte[] fake = { 'R','I','F','F', 22,0,0,0, 'W','E','B','P', 'V','P','8',' ', 10,0,0,0,
                0,0,0, (byte)0x9d,1,0x2a, 1,0, 1,0 };

        assertThrows(com.animelist.backend.exception.BusinessException.class,
                () -> new ProfileAssetService(repository, currentUser).save(ProfileAssetType.AVATAR,
                        new MockMultipartFile("file", "avatar.webp", "image/webp", fake)));
        verify(repository, never()).save(any());
    }

    @Test void rejectsAvatarDimensionsBeforeDecodingThePixels() throws Exception {
        UserProfileAssetRepository repository = mock(UserProfileAssetRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        AppUser user = new AppUser(); user.setId(4L);
        when(currentUser.require()).thenReturn(user);
        byte[] oversized = png(1025, 1);

        assertThrows(com.animelist.backend.exception.BusinessException.class,
                () -> new ProfileAssetService(repository, currentUser).save(ProfileAssetType.AVATAR,
                        new MockMultipartFile("file", "avatar.png", "image/png", oversized)));
        verify(repository, never()).save(any());
    }

    private byte[] png() throws Exception {
        return png(2, 2);
    }

    private byte[] png(int width, int height) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }
}
