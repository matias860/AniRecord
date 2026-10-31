package com.animelist.backend.service;

import com.animelist.backend.dto.anilist.AniListMediaResponse;
import com.animelist.backend.entity.Media;
import com.animelist.backend.repository.MediaRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaMetadataService {

    private final MediaRepository mediaRepository;

    public MediaMetadataService(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    /**
     * Persists metadata already returned by a season request. It never creates new
     * media or touches user-specific library fields, and it only writes rows whose
     * values actually changed.
     */
    @Transactional
    public int refreshExistingSeasonMedia(List<AniListMediaResponse> seasonMedia) {
        if (seasonMedia == null || seasonMedia.isEmpty()) return 0;

        Map<Integer, AniListMediaResponse> externalById = seasonMedia.stream()
                .filter(item -> item != null && item.anilistId() != null)
                .collect(Collectors.toMap(
                        AniListMediaResponse::anilistId,
                        item -> item,
                        (first, ignored) -> first,
                        HashMap::new
                ));
        if (externalById.isEmpty()) return 0;

        List<Media> changed = mediaRepository.findAllByAnilistIdIn(externalById.keySet()).stream()
                .filter(media -> applySeasonMetadata(media, externalById.get(media.getAnilistId())))
                .toList();
        if (changed.isEmpty()) return 0;

        mediaRepository.saveAll(changed);
        return changed.size();
    }

    private boolean applySeasonMetadata(Media target, AniListMediaResponse source) {
        boolean changed = false;
        changed |= replace(target::getTitle, target::setTitle, source.title());
        changed |= replace(target::getRomajiTitle, target::setRomajiTitle, source.romajiTitle());
        changed |= replace(target::getEnglishTitle, target::setEnglishTitle, source.englishTitle());
        changed |= replace(target::getNativeTitle, target::setNativeTitle, source.nativeTitle());
        changed |= replace(target::getAdultContent, target::setAdultContent, Boolean.TRUE.equals(source.adultContent()));
        changed |= replace(target::getCoverImage, target::setCoverImage, source.coverImage());
        changed |= replace(target::getBannerImage, target::setBannerImage, source.bannerImage());
        changed |= replace(target::getSynopsis, target::setSynopsis, source.synopsis());
        changed |= replace(target::getGenres, target::setGenres,
                source.genres() == null ? null : String.join("|", source.genres()));
        changed |= replace(target::getTotalUnits, target::setTotalUnits, source.totalUnits());
        changed |= replace(target::getReleaseStartDate, target::setReleaseStartDate, source.releaseStartDate());
        changed |= replace(target::getReleaseEndDate, target::setReleaseEndDate, source.releaseEndDate());
        changed |= replace(target::getReleaseStatus, target::setReleaseStatus, source.releaseStatus());
        changed |= replace(target::getReleaseSeason, target::setReleaseSeason, source.releaseSeason());
        changed |= replace(target::getReleaseYear, target::setReleaseYear, source.releaseYear());
        if (changed) target.setLastSyncedAt(LocalDateTime.now());
        return changed;
    }

    private <T> boolean replace(Supplier<T> getter, Consumer<T> setter, T newValue) {
        if (Objects.equals(getter.get(), newValue)) return false;
        setter.accept(newValue);
        return true;
    }
}
