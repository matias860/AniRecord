package com.animelist.backend.service;

import com.animelist.backend.dto.anilist.*;
import com.animelist.backend.dto.library.LibraryImportResponse;
import com.animelist.backend.entity.*;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.repository.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LibraryImportWriter {
    public record BatchResult(int processed, int created, int updated) {}
    public record FavoriteResult(int created, int characters, int staff) {}
    private final LibraryEntryRepository library;
    private final MediaRepository media;
    private final AppUserRepository users;
    private final PersonFavoriteRepository favorites;

    public LibraryImportWriter(LibraryEntryRepository library, MediaRepository media,
            AppUserRepository users, PersonFavoriteRepository favorites) {
        this.library = library;
        this.media = media;
        this.users = users;
        this.favorites = favorites;
    }

    @Transactional
    public LibraryImportResponse apply(Long userId, AniListLibraryExport externalLibrary,
            List<AniListPersonFavorite> externalFavorites) {
        AppUser profile = users.findById(userId).orElseThrow();
        int created = 0, updated = 0, anime = 0, manga = 0, characters = 0, staff = 0;
        for (AniListLibraryItem item : externalLibrary.entries()) {
            LibraryEntry existing = library.findByUserIdAndMediaAnilistId(userId, item.media().anilistId()).orElse(null);
            if (existing != null) {
                copyExternalMedia(item.media(), existing.getMedia());
                media.save(existing.getMedia());
                copyTracking(item, existing);
                library.save(existing);
                updated++;
            } else {
                Media localMedia = media.findByAnilistId(item.media().anilistId()).orElseGet(Media::new);
                copyExternalMedia(item.media(), localMedia);
                localMedia = media.save(localMedia);
                LibraryEntry entry = new LibraryEntry();
                entry.setMedia(localMedia); entry.setUser(profile); entry.setProgress(item.progress());
                entry.setPersonalScore(item.personalScore()); entry.setRewatchCount(item.rewatchCount());
                entry.setTrackingStatus(item.trackingStatus()); entry.setStartedAt(item.startedAt());
                entry.setCompletedAt(item.completedAt());
                library.save(entry);
                created++;
            }
            if (item.media().mediaType() == MediaType.ANIME) anime++; else manga++;
        }
        Set<String> existingKeys = favorites.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(item -> item.getPersonType().name() + ":" + item.getAnilistId()).collect(Collectors.toSet());
        for (AniListPersonFavorite item : externalFavorites) {
            String key = item.personType().name() + ":" + item.anilistId();
            if (!existingKeys.add(key)) continue;
            PersonFavorite favorite = new PersonFavorite();
            favorite.setUser(profile); favorite.setAnilistId(item.anilistId()); favorite.setPersonType(item.personType());
            favorite.setName(item.name()); favorite.setNativeName(item.nativeName()); favorite.setImageUrl(item.imageUrl());
            favorites.save(favorite);
            if (item.personType() == com.animelist.backend.enums.PersonType.CHARACTER) characters++; else staff++;
        }
        if (profile.getLastAniListSyncAt() == null) {
            profile.setDisplayName(externalLibrary.username()); profile.setAvatarUrl(externalLibrary.avatarUrl());
            profile.setBannerUrl(externalLibrary.bannerUrl());
        }
        profile.setLastAniListSyncAt(LocalDateTime.now()); users.save(profile);
        return new LibraryImportResponse(externalLibrary.username(), anime + manga, anime, manga, created, updated,
                characters + staff, characters, staff);
    }

    @Transactional
    public BatchResult applyBatch(Long userId, List<AniListLibraryItem> items,
            Map<Integer, AniListImportService.LocalState> expectedBefore) {
        AppUser profile = users.findById(userId).orElseThrow();
        int created = 0;
        int updated = 0;
        for (AniListLibraryItem item : items) {
            int anilistId = item.media().anilistId();
            LibraryEntry existing = library.findForUpdateByUserIdAndMediaAnilistId(userId, anilistId).orElse(null);
            AniListImportService.LocalState expected = expectedBefore.get(anilistId);
            if (existing == null && expected != null || existing != null && !sameState(existing, expected)) {
                throw new com.animelist.backend.exception.BusinessException(
                        item.media().title() + ": la biblioteca cambió durante la sincronización. Iníciala de nuevo.");
            }
            if (existing == null) {
                Media localMedia = media.findByAnilistId(anilistId).orElseGet(Media::new);
                copyExternalMedia(item.media(), localMedia);
                localMedia = media.save(localMedia);
                LibraryEntry entry = new LibraryEntry();
                entry.setMedia(localMedia);
                entry.setUser(profile);
                copyTracking(item, entry);
                library.save(entry);
                created++;
            } else {
                copyExternalMedia(item.media(), existing.getMedia());
                media.save(existing.getMedia());
                copyTracking(item, existing);
                library.save(existing);
                updated++;
            }
        }
        return new BatchResult(items.size(), created, updated);
    }

    @Transactional
    public FavoriteResult complete(Long userId, AniListLibraryExport externalLibrary,
            List<AniListPersonFavorite> externalFavorites) {
        AppUser profile = users.findById(userId).orElseThrow();
        Set<String> existingKeys = favorites.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(item -> item.getPersonType().name() + ":" + item.getAnilistId()).collect(Collectors.toSet());
        int created = 0, characters = 0, staff = 0;
        for (AniListPersonFavorite item : externalFavorites) {
            String key = item.personType().name() + ":" + item.anilistId();
            if (!existingKeys.add(key)) continue;
            PersonFavorite favorite = new PersonFavorite();
            favorite.setUser(profile); favorite.setAnilistId(item.anilistId()); favorite.setPersonType(item.personType());
            favorite.setName(item.name()); favorite.setNativeName(item.nativeName()); favorite.setImageUrl(item.imageUrl());
            favorites.save(favorite);
            created++;
            if (item.personType() == com.animelist.backend.enums.PersonType.CHARACTER) characters++; else staff++;
        }
        if (profile.getLastAniListSyncAt() == null) {
            profile.setDisplayName(externalLibrary.username()); profile.setAvatarUrl(externalLibrary.avatarUrl());
            profile.setBannerUrl(externalLibrary.bannerUrl());
        }
        profile.setLastAniListSyncAt(LocalDateTime.now());
        users.save(profile);
        return new FavoriteResult(created, characters, staff);
    }

    private static void copyTracking(AniListLibraryItem item, LibraryEntry entry) {
        entry.setProgress(item.progress());
        entry.setPersonalScore(item.personalScore());
        entry.setRewatchCount(item.rewatchCount());
        entry.setTrackingStatus(item.trackingStatus());
        entry.setStartedAt(item.startedAt());
        entry.setCompletedAt(item.completedAt());
    }

    private static boolean sameState(LibraryEntry entry, AniListImportService.LocalState expected) {
        if (expected == null) return false;
        return Objects.equals(entry.getProgress(), expected.progress())
                && sameScore(entry.getPersonalScore(), expected.score())
                && Objects.equals(entry.getRewatchCount(), expected.repeats())
                && entry.getTrackingStatus() == expected.status()
                && Objects.equals(entry.getStartedAt(), expected.startedAt())
                && Objects.equals(entry.getCompletedAt(), expected.completedAt());
    }

    private static boolean sameScore(java.math.BigDecimal left, java.math.BigDecimal right) {
        if (left == null || right == null) return left == right;
        return left.compareTo(right) == 0;
    }

    private void copyExternalMedia(AniListMediaResponse source, Media target) {
        target.setAnilistId(source.anilistId()); target.setMediaType(source.mediaType()); target.setMediaFormat(source.mediaFormat());
        target.setCountryOfOrigin(source.countryOfOrigin()); target.setTitle(source.title()); target.setRomajiTitle(source.romajiTitle());
        target.setCoverImage(source.coverImage()); target.setBannerImage(source.bannerImage()); target.setSynopsis(source.synopsis());
        target.setGenres(source.genres() == null ? null : String.join("|", source.genres()));
        target.setTags(source.tags() == null ? null : source.tags().stream().map(tag -> tag.name()).collect(Collectors.joining("|")));
        target.setTotalUnits(source.totalUnits()); target.setReleaseStartDate(source.releaseStartDate());
        target.setReleaseEndDate(source.releaseEndDate()); target.setReleaseStatus(source.releaseStatus());
        target.setReleaseSeason(source.releaseSeason()); target.setReleaseYear(source.releaseYear());
        target.setLastSyncedAt(LocalDateTime.now());
    }
}
