package com.animelist.backend.service;

import com.animelist.backend.dto.anilist.*;
import com.animelist.backend.dto.library.LibraryImportResponse;
import com.animelist.backend.entity.*;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.repository.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LibraryImportWriter {
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
