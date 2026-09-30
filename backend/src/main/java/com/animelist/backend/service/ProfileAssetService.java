package com.animelist.backend.service;

import com.animelist.backend.entity.AppUser;
import com.animelist.backend.entity.UserProfileAsset;
import com.animelist.backend.enums.ProfileAssetType;
import com.animelist.backend.exception.BusinessException;
import com.animelist.backend.exception.ResourceNotFoundException;
import com.animelist.backend.repository.UserProfileAssetRepository;
import java.util.Locale;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfileAssetService {
    private static final Semaphore IMAGE_DECODER = new Semaphore(1, true);
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final UserProfileAssetRepository repository;
    private final CurrentUserService currentUser;

    public ProfileAssetService(UserProfileAssetRepository repository, CurrentUserService currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @Transactional
    public void save(ProfileAssetType type, MultipartFile file) {
        AppUser user = currentUser.require();
        long max = type == ProfileAssetType.AVATAR ? 2L * 1024 * 1024 : 6L * 1024 * 1024;
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (file.isEmpty()) throw new BusinessException("Selecciona una imagen");
        if (file.getSize() > max) throw new BusinessException(type == ProfileAssetType.AVATAR
                ? "El avatar no puede superar 2 MB" : "El banner no puede superar 6 MB");
        if (!ALLOWED_TYPES.contains(contentType)) throw new BusinessException("Usá una imagen JPG, PNG o WebP");
        if (!IMAGE_DECODER.tryAcquire()) throw new BusinessException("Ya se está procesando otra imagen. Intentá nuevamente en unos segundos");
        byte[] bytes;
        try {
            bytes = file.getBytes();
            if (!matchesSignature(bytes, contentType)) throw new BusinessException("El archivo no contiene una imagen válida");
            validateImage(bytes, type);
        } catch (java.io.IOException ex) {
            throw new BusinessException("No se pudo leer la imagen");
        } finally {
            IMAGE_DECODER.release();
        }
        UserProfileAsset asset = repository.findByUserIdAndAssetType(user.getId(), type).orElseGet(UserProfileAsset::new);
        asset.setUser(user);
        asset.setAssetType(type);
        asset.setContentType(contentType);
        asset.setData(bytes);
        repository.save(asset);
    }

    @Transactional(readOnly = true)
    public AssetData get(ProfileAssetType type) {
        AppUser user = currentUser.require();
        return getForUser(user.getId(), type);
    }

    @Transactional(readOnly = true)
    public AssetData getForUser(Long userId, ProfileAssetType type) {
        UserProfileAsset asset = repository.findByUserIdAndAssetType(userId, type)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la imagen"));
        return new AssetData(asset.getContentType(), asset.getData());
    }

    @Transactional
    public void delete(ProfileAssetType type) {
        AppUser user = currentUser.require();
        repository.deleteByUserIdAndAssetType(user.getId(), type);
    }

    private boolean matchesSignature(byte[] bytes, String type) {
        if (type.equals("image/jpeg")) return bytes.length > 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
        if (type.equals("image/png")) return bytes.length > 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47;
        return bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private void validateImage(byte[] bytes, ProfileAssetType type) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BusinessException("El archivo no contiene una imagen válida");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(type, width, height);
                if (reader.read(0) == null) throw new BusinessException("El archivo no contiene una imagen válida");
            } catch (RuntimeException | IOException exception) {
                if (exception instanceof BusinessException businessException) throw businessException;
                throw new BusinessException("El archivo no contiene una imagen válida");
            } finally {
                reader.dispose();
            }
        }
    }

    private void validateDimensions(ProfileAssetType type, int width, int height) {
        int maxWidth = type == ProfileAssetType.AVATAR ? 1024 : 2400;
        int maxHeight = type == ProfileAssetType.AVATAR ? 1024 : 1200;
        long maxPixels = type == ProfileAssetType.AVATAR ? 1_048_576L : 2_880_000L;
        if (width <= 0 || height <= 0 || width > maxWidth || height > maxHeight || (long) width * height > maxPixels) {
            throw new BusinessException("La imagen tiene dimensiones demasiado grandes");
        }
    }

    public record AssetData(String contentType, byte[] bytes) {}
}
