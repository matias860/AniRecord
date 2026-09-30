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
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfileAssetService {
    private static final long MAX_PIXELS = 12_000_000L;
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
        try {
            byte[] bytes = file.getBytes();
            if (!matchesSignature(bytes, contentType)) throw new BusinessException("El archivo no contiene una imagen válida");
            validateImage(bytes, contentType);
            UserProfileAsset asset = repository.findByUserIdAndAssetType(user.getId(), type).orElseGet(UserProfileAsset::new);
            asset.setUser(user);
            asset.setAssetType(type);
            asset.setContentType(contentType);
            asset.setData(bytes);
            repository.save(asset);
        } catch (java.io.IOException ex) {
            throw new BusinessException("No se pudo leer la imagen");
        }
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

    private void validateImage(byte[] bytes, String type) throws IOException {
        if ("image/webp".equals(type)) {
            validateWebP(bytes);
            return;
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BusinessException("El archivo no contiene una imagen válida");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);
                if (reader.read(0) == null) throw new BusinessException("El archivo no contiene una imagen válida");
            } catch (RuntimeException | IOException exception) {
                if (exception instanceof BusinessException businessException) throw businessException;
                throw new BusinessException("El archivo no contiene una imagen válida");
            } finally {
                reader.dispose();
            }
        }
    }

    private void validateWebP(byte[] bytes) {
        long declaredEnd = 8L + littleEndian32(bytes, 4);
        if (declaredEnd < 20 || declaredEnd != bytes.length) throw new BusinessException("El archivo no contiene una imagen válida");
        int offset = 12;
        while (offset + 8 <= declaredEnd) {
            String chunk = new String(bytes, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
            long chunkSize = littleEndian32(bytes, offset + 4);
            long dataEnd = offset + 8L + chunkSize;
            if (dataEnd > declaredEnd) throw new BusinessException("El archivo no contiene una imagen válida");
            int data = offset + 8;
            if ("VP8X".equals(chunk) && chunkSize >= 10) {
                validateDimensions(1 + littleEndian24(bytes, data + 4), 1 + littleEndian24(bytes, data + 7));
            }
            if ("VP8L".equals(chunk) && chunkSize >= 5 && (bytes[data] & 0xff) == 0x2f) {
                int width = 1 + ((bytes[data + 1] & 0xff) | ((bytes[data + 2] & 0x3f) << 8));
                int height = 1 + (((bytes[data + 2] & 0xc0) >> 6) | ((bytes[data + 3] & 0xff) << 2) | ((bytes[data + 4] & 0x0f) << 10));
                validateDimensions(width, height);
                return;
            }
            if ("VP8 ".equals(chunk) && chunkSize >= 10 && (bytes[data + 3] & 0xff) == 0x9d
                    && (bytes[data + 4] & 0xff) == 0x01 && (bytes[data + 5] & 0xff) == 0x2a) {
                int width = ((bytes[data + 6] & 0xff) | ((bytes[data + 7] & 0x3f) << 8));
                int height = ((bytes[data + 8] & 0xff) | ((bytes[data + 9] & 0x3f) << 8));
                validateDimensions(width, height);
                return;
            }
            offset = (int) (dataEnd + (chunkSize & 1));
        }
        throw new BusinessException("El archivo no contiene una imagen WebP válida");
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
            throw new BusinessException("La imagen tiene dimensiones demasiado grandes");
        }
    }

    private long littleEndian32(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffL) | ((bytes[offset + 1] & 0xffL) << 8)
                | ((bytes[offset + 2] & 0xffL) << 16) | ((bytes[offset + 3] & 0xffL) << 24);
    }

    private int littleEndian24(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8) | ((bytes[offset + 2] & 0xff) << 16);
    }

    public record AssetData(String contentType, byte[] bytes) {}
}
