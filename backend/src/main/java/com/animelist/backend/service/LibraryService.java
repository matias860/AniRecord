package com.animelist.backend.service;

import com.animelist.backend.dto.anilist.AiringMediaResponse;
import com.animelist.backend.dto.anilist.AniListMediaResponse;
import com.animelist.backend.dto.anilist.AniListLibraryExport;
import com.animelist.backend.dto.anilist.AniListLibraryItem;
import com.animelist.backend.dto.library.AddLibraryEntryRequest;
import com.animelist.backend.dto.library.LibraryEntryResponse;
import com.animelist.backend.dto.library.LibraryEntrySummaryResponse;
import com.animelist.backend.dto.library.LibraryImportResponse;
import com.animelist.backend.dto.library.UpdateLibraryEntryRequest;
import com.animelist.backend.entity.LibraryEntry;
import com.animelist.backend.entity.Media;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.enums.ActivityType;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.TrackingStatus;
import com.animelist.backend.exception.BusinessException;
import com.animelist.backend.exception.ResourceNotFoundException;
import com.animelist.backend.repository.LibraryEntryRepository;
import com.animelist.backend.repository.MediaRepository;
import com.animelist.backend.repository.AppUserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LibraryService {

    private final LibraryEntryRepository libraryEntryRepository;
    private final MediaRepository mediaRepository;
    private final AniListClient aniListClient;
    private final AppUserRepository appUserRepository;
    private final ActivityHistoryService activityHistoryService;
    private final CurrentUserService currentUser;

    public LibraryService(
            LibraryEntryRepository libraryEntryRepository,
            MediaRepository mediaRepository,
            AniListClient aniListClient,
            AppUserRepository appUserRepository,
            ActivityHistoryService activityHistoryService,
            CurrentUserService currentUser
    ) {
        this.libraryEntryRepository = libraryEntryRepository;
        this.mediaRepository = mediaRepository;
        this.aniListClient = aniListClient;
        this.appUserRepository = appUserRepository;
        this.activityHistoryService = activityHistoryService;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<LibraryEntrySummaryResponse> findAll(MediaType mediaType, TrackingStatus status) {
        return libraryEntryRepository.findSummaries(currentUser.require().getId(), mediaType, status);
    }

    @Transactional(readOnly = true)
    public LibraryEntryResponse findById(Long id) {
        return toResponse(findEntry(id));
    }

    @Transactional
    public LibraryEntryResponse add(AddLibraryEntryRequest request) {
        AppUser user=currentUser.require();
        if (libraryEntryRepository.existsByUserIdAndMediaAnilistId(user.getId(), request.anilistId())) {
            throw new BusinessException("La obra ya está en tu biblioteca");
        }

        AniListMediaResponse externalMedia = aniListClient.getMedia(request.anilistId());
        Media media = mediaRepository.findByAnilistId(request.anilistId())
                .orElseGet(Media::new);
        copyExternalMedia(externalMedia, media);
        media = mediaRepository.save(media);

        LibraryEntry entry = new LibraryEntry();
        entry.setUser(user);
        entry.setMedia(media);
        entry.setTrackingStatus(request.trackingStatus() == null
                ? TrackingStatus.PLANNED
                : request.trackingStatus());
        applyAutomaticRules(entry);

        entry = libraryEntryRepository.save(entry);
        activityHistoryService.record(ActivityType.ADDED, entry, null, null, null, null);
        return toResponse(entry);
    }

    @Transactional
    public LibraryEntryResponse update(Long id, UpdateLibraryEntryRequest request) {
        LibraryEntry entry = findEntry(id);
        int previousProgress = entry.getProgress();
        TrackingStatus previousStatus = entry.getTrackingStatus();
        BigDecimal previousScore = entry.getPersonalScore();
        int previousRewatchCount = entry.getRewatchCount();

        if (request.progress() != null) {
            validateProgress(request.progress(), entry.getMedia().getTotalUnits());
            entry.setProgress(request.progress());
            if (request.trackingStatus() == null
                    && entry.getTrackingStatus() == TrackingStatus.COMPLETED
                    && !isTotalProgress(entry)) {
                entry.setTrackingStatus(TrackingStatus.IN_PROGRESS);
            }
        }
        if (Boolean.TRUE.equals(request.clearScore())) {
            entry.setPersonalScore(null);
        } else if (request.personalScore() != null) {
            entry.setPersonalScore(request.personalScore());
        }
        if (request.rewatchCount() != null) {
            entry.setRewatchCount(request.rewatchCount());
        }
        if (request.trackingStatus() != null) {
            entry.setTrackingStatus(request.trackingStatus());
        }
        if (request.startedAt() != null) {
            entry.setStartedAt(request.startedAt());
        }
        if (request.completedAt() != null) {
            entry.setCompletedAt(request.completedAt());
        }

        applyAutomaticRules(entry);
        entry = libraryEntryRepository.save(entry);
        activityHistoryService.record(ActivityType.UPDATED, entry, previousProgress,
                previousStatus, previousScore, previousRewatchCount);
        return toResponse(entry);
    }

    @Transactional
    public LibraryEntryResponse incrementProgress(Long id) {
        LibraryEntry entry = findEntry(id);
        int previousProgress = entry.getProgress();
        TrackingStatus previousStatus = entry.getTrackingStatus();
        BigDecimal previousScore = entry.getPersonalScore();
        int previousRewatchCount = entry.getRewatchCount();
        int nextProgress = entry.getProgress() + 1;
        validateProgress(nextProgress, entry.getMedia().getTotalUnits());
        entry.setProgress(nextProgress);
        applyAutomaticRules(entry);
        entry = libraryEntryRepository.save(entry);
        activityHistoryService.record(ActivityType.PROGRESS, entry, previousProgress,
                previousStatus, previousScore, previousRewatchCount);
        return toResponse(entry);
    }

    @Transactional
    public LibraryEntryResponse decrementProgress(Long id) {
        LibraryEntry entry = findEntry(id);
        int previousProgress = entry.getProgress();
        TrackingStatus previousStatus = entry.getTrackingStatus();
        BigDecimal previousScore = entry.getPersonalScore();
        int previousRewatchCount = entry.getRewatchCount();
        if (entry.getProgress() == 0) {
            throw new BusinessException("El progreso ya está en cero");
        }
        entry.setProgress(entry.getProgress() - 1);
        if (entry.getTrackingStatus() == TrackingStatus.COMPLETED) {
            entry.setTrackingStatus(TrackingStatus.IN_PROGRESS);
            entry.setCompletedAt(null);
        }
        entry = libraryEntryRepository.save(entry);
        activityHistoryService.record(ActivityType.PROGRESS, entry, previousProgress,
                previousStatus, previousScore, previousRewatchCount);
        return toResponse(entry);
    }

    @Transactional
    public LibraryEntryResponse synchronize(Long id) {
        LibraryEntry entry = findEntry(id);
        AniListMediaResponse externalMedia = aniListClient.getMedia(entry.getMedia().getAnilistId());
        copyExternalMedia(externalMedia, entry.getMedia());
        validateProgress(entry.getProgress(), entry.getMedia().getTotalUnits());
        mediaRepository.save(entry.getMedia());
        return toResponse(entry);
    }

    @Transactional
    public void delete(Long id) {
        LibraryEntry entry = findEntry(id);
        activityHistoryService.recordRemoval(entry);
        libraryEntryRepository.delete(entry);
    }

    @Transactional(readOnly = true)
    public List<AiringMediaResponse> findUpcomingEpisodes() {
        List<LibraryEntry> entries = libraryEntryRepository
                .findByUserIdAndMediaMediaTypeAndTrackingStatus(currentUser.require().getId(), MediaType.ANIME, TrackingStatus.IN_PROGRESS);
        List<Integer> ids = entries.stream()
                .map(entry -> entry.getMedia().getAnilistId())
                .toList();

        Map<Integer, LibraryEntry> entriesByAniListId = entries.stream()
                .collect(Collectors.toMap(
                        entry -> entry.getMedia().getAnilistId(),
                        Function.identity()
                ));

        return aniListClient.getAiringMedia(ids).stream()
                .map(media -> {
                    LibraryEntry entry = entriesByAniListId.get(media.anilistId());
                    return new AiringMediaResponse(
                            media.anilistId(),
                            media.title(),
                            media.coverImage(),
                            entry.getProgress(),
                            media.nextAiringEpisode()
                    );
                })
                .sorted(Comparator.comparing(item -> item.nextAiringEpisode().airingAt()))
                .toList();
    }

    private LibraryEntry findEntry(Long id) {
        return libraryEntryRepository.findWithMediaByIdAndUserId(id, currentUser.require().getId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la entrada de biblioteca"));
    }

    private void validateProgress(int progress, Integer totalUnits) {
        if (progress < 0) {
            throw new BusinessException("El progreso no puede ser negativo");
        }
        if (totalUnits != null && progress > totalUnits) {
            throw new BusinessException("El progreso no puede superar el total de episodios o capítulos");
        }
    }

    private void applyAutomaticRules(LibraryEntry entry) {
        Integer totalUnits = entry.getMedia().getTotalUnits();

        if (entry.getTrackingStatus() == TrackingStatus.PLANNED) {
            entry.setProgress(0);
            entry.setStartedAt(null);
            entry.setCompletedAt(null);
            return;
        }

        if (entry.getTrackingStatus() == TrackingStatus.COMPLETED && totalUnits != null) {
            entry.setProgress(totalUnits);
        }

        if (entry.getProgress() > 0 && entry.getStartedAt() == null) {
            entry.setStartedAt(LocalDate.now());
        }

        if (totalUnits != null && entry.getProgress().equals(totalUnits)) {
            entry.setTrackingStatus(TrackingStatus.COMPLETED);
            if (entry.getCompletedAt() == null) {
                entry.setCompletedAt(LocalDate.now());
            }
        } else if (entry.getTrackingStatus() == TrackingStatus.COMPLETED) {
            entry.setCompletedAt(entry.getCompletedAt() == null ? LocalDate.now() : entry.getCompletedAt());
        } else {
            entry.setCompletedAt(null);
        }
    }

    @Transactional
    public LibraryImportResponse importFromAniList() {
        AppUser user=currentUser.require();
        return importForUser(user.getId(), user.getAnilistUsername());
    }

    @Transactional
    public synchronized LibraryImportResponse importForUser(Long userId, String username) {
        AppUser profile=appUserRepository.findById(userId).orElseThrow();
        AniListLibraryExport externalLibrary = aniListClient.getUserLibrary(username.trim());
        int created = 0;
        int updated = 0;
        int anime = 0;
        int manga = 0;

        for (AniListLibraryItem item : externalLibrary.entries()) {
            Media media = mediaRepository.findByAnilistId(item.media().anilistId()).orElseGet(Media::new);
            copyExternalMedia(item.media(), media);
            media = mediaRepository.save(media);

            LibraryEntry entry = libraryEntryRepository.findByUserIdAndMediaAnilistId(userId, item.media().anilistId()).orElse(null);
            if (entry == null) {
                entry = new LibraryEntry();
                entry.setMedia(media);
                entry.setUser(profile);
                created++;
            } else {
                updated++;
            }
            entry.setProgress(item.progress());
            entry.setPersonalScore(item.personalScore());
            entry.setRewatchCount(item.rewatchCount());
            entry.setTrackingStatus(item.trackingStatus());
            entry.setStartedAt(item.startedAt());
            entry.setCompletedAt(item.completedAt());
            libraryEntryRepository.save(entry);

            if (item.media().mediaType() == MediaType.ANIME) anime++; else manga++;
        }

        profile.setDisplayName(externalLibrary.username());
        profile.setAvatarUrl(externalLibrary.avatarUrl());
        profile.setBannerUrl(externalLibrary.bannerUrl());
        profile.setLastAniListSyncAt(LocalDateTime.now());
        appUserRepository.save(profile);

        return new LibraryImportResponse(externalLibrary.username(), anime + manga, anime, manga, created, updated);
    }

    private boolean isTotalProgress(LibraryEntry entry) {
        Integer totalUnits = entry.getMedia().getTotalUnits();
        return totalUnits != null && entry.getProgress().equals(totalUnits);
    }

    private void copyExternalMedia(AniListMediaResponse source, Media target) {
        target.setAnilistId(source.anilistId());
        target.setMediaType(source.mediaType());
        target.setTitle(source.title());
        target.setRomajiTitle(source.romajiTitle());
        target.setCoverImage(source.coverImage());
        target.setBannerImage(source.bannerImage());
        target.setSynopsis(source.synopsis());
        target.setTotalUnits(source.totalUnits());
        target.setReleaseStartDate(source.releaseStartDate());
        target.setReleaseEndDate(source.releaseEndDate());
        target.setReleaseStatus(source.releaseStatus());
        target.setReleaseSeason(source.releaseSeason());
        target.setReleaseYear(source.releaseYear());
        target.setLastSyncedAt(LocalDateTime.now());
    }

    private LibraryEntryResponse toResponse(LibraryEntry entry) {
        Media media = entry.getMedia();
        return new LibraryEntryResponse(
                entry.getId(),
                media.getAnilistId(),
                media.getMediaType(),
                media.getTitle(),
                media.getRomajiTitle(),
                media.getCoverImage(),
                media.getBannerImage(),
                media.getSynopsis(),
                media.getTotalUnits(),
                media.getReleaseStartDate(),
                media.getReleaseEndDate(),
                media.getReleaseStatus(),
                media.getReleaseSeason(),
                media.getReleaseYear(),
                entry.getProgress(),
                entry.getPersonalScore(),
                entry.getRewatchCount(),
                entry.getTrackingStatus(),
                entry.getStartedAt(),
                entry.getCompletedAt(),
                entry.getCreatedAt(),
                entry.getUpdatedAt()
        );
    }
}
