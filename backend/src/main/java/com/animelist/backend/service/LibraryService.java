package com.animelist.backend.service;

import com.animelist.backend.dto.anilist.AiringMediaResponse;
import com.animelist.backend.dto.anilist.AniListMediaResponse;
import com.animelist.backend.dto.anilist.AniListLibraryExport;
import com.animelist.backend.dto.anilist.AniListLibraryItem;
import com.animelist.backend.dto.anilist.AniListPersonFavorite;
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
import com.animelist.backend.enums.ReleaseSeason;
import com.animelist.backend.enums.TrackingStatus;
import com.animelist.backend.exception.BusinessException;
import com.animelist.backend.exception.ResourceNotFoundException;
import com.animelist.backend.repository.LibraryEntryRepository;
import com.animelist.backend.repository.MediaRepository;
import com.animelist.backend.repository.PersonFavoriteRepository;
import com.animelist.backend.repository.AppUserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final AniListAuthService aniListAuthService;
    private final LibraryImportWriter importWriter;
    private final ConcurrentHashMap<Long, ImportLock> importLocks = new ConcurrentHashMap<>();

    @Autowired
    public LibraryService(
            LibraryEntryRepository libraryEntryRepository,
            MediaRepository mediaRepository,
            AniListClient aniListClient,
            AppUserRepository appUserRepository,
            ActivityHistoryService activityHistoryService,
            CurrentUserService currentUser,
            AniListAuthService aniListAuthService,
            LibraryImportWriter importWriter
    ) {
        this.libraryEntryRepository = libraryEntryRepository;
        this.mediaRepository = mediaRepository;
        this.aniListClient = aniListClient;
        this.appUserRepository = appUserRepository;
        this.activityHistoryService = activityHistoryService;
        this.currentUser = currentUser;
        this.aniListAuthService = aniListAuthService;
        this.importWriter = importWriter;
    }

    LibraryService(LibraryEntryRepository libraryEntryRepository, MediaRepository mediaRepository,
            AniListClient aniListClient, AppUserRepository appUserRepository,
            ActivityHistoryService activityHistoryService, CurrentUserService currentUser,
            AniListAuthService aniListAuthService, PersonFavoriteRepository personFavoriteRepository) {
        this(libraryEntryRepository, mediaRepository, aniListClient, appUserRepository, activityHistoryService,
                currentUser, aniListAuthService,
                new LibraryImportWriter(libraryEntryRepository, mediaRepository, appUserRepository, personFavoriteRepository));
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

        AniListMediaResponse externalMedia = aniListClient.getMediaForLibrary(request.anilistId());
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
        LibraryEntry entry = findEntryForUpdate(id);
        int previousProgress = entry.getProgress();
        TrackingStatus previousStatus = entry.getTrackingStatus();
        BigDecimal previousScore = entry.getPersonalScore();
        int previousRewatchCount = entry.getRewatchCount();

        if (request.progress() != null) {
            if (request.progress() > previousProgress) validateReleased(entry);
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
        LibraryEntry entry = findEntryForUpdate(id);
        int previousProgress = entry.getProgress();
        TrackingStatus previousStatus = entry.getTrackingStatus();
        BigDecimal previousScore = entry.getPersonalScore();
        int previousRewatchCount = entry.getRewatchCount();
        int nextProgress = entry.getProgress() + 1;
        validateReleased(entry);
        validateProgress(nextProgress, entry.getMedia().getTotalUnits());
        if (entry.getTrackingStatus() == TrackingStatus.PLANNED) {
            entry.setTrackingStatus(TrackingStatus.IN_PROGRESS);
        }
        entry.setProgress(nextProgress);
        applyAutomaticRules(entry);
        entry = libraryEntryRepository.save(entry);
        activityHistoryService.record(ActivityType.PROGRESS, entry, previousProgress,
                previousStatus, previousScore, previousRewatchCount);
        return toResponse(entry);
    }

    @Transactional
    public LibraryEntryResponse decrementProgress(Long id) {
        LibraryEntry entry = findEntryForUpdate(id);
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
        AniListMediaResponse externalMedia = aniListClient.getMediaForLibrary(entry.getMedia().getAnilistId());
        copyExternalMedia(externalMedia, entry.getMedia());
        validateProgress(entry.getProgress(), entry.getMedia().getTotalUnits());
        mediaRepository.save(entry.getMedia());
        return toResponse(entry);
    }

    @Transactional
    public void delete(Long id) {
        LibraryEntry entry = findEntryForUpdate(id);
        activityHistoryService.recordRemoval(entry);
        libraryEntryRepository.delete(entry);
    }

    @Transactional(readOnly = true)
    public List<AiringMediaResponse> findUpcomingEpisodes() {
        Long userId = currentUser.require().getId();
        LocalDate today = LocalDate.now();
        ReleaseSeason currentSeason = switch ((today.getMonthValue() - 1) / 3) {
            case 0 -> ReleaseSeason.WINTER;
            case 1 -> ReleaseSeason.SPRING;
            case 2 -> ReleaseSeason.SUMMER;
            default -> ReleaseSeason.FALL;
        };
        List<LibraryEntry> entries = new ArrayList<>(libraryEntryRepository
                .findByUserIdAndMediaMediaTypeAndTrackingStatus(userId, MediaType.ANIME, TrackingStatus.IN_PROGRESS));
        entries.addAll(libraryEntryRepository
                .findByUserIdAndMediaMediaTypeAndTrackingStatusAndMediaReleaseSeasonAndMediaReleaseYear(
                        userId, MediaType.ANIME, TrackingStatus.PLANNED, currentSeason, today.getYear()));
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

    private LibraryEntry findEntryForUpdate(Long id) {
        return libraryEntryRepository.findWithMediaForUpdateByIdAndUserId(id, currentUser.require().getId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la entrada de biblioteca"));
    }

    private void validateReleased(LibraryEntry entry) {
        if (entry.getMedia().getMediaType() == MediaType.ANIME
                && entry.getMedia().getReleaseStatus() == com.animelist.backend.enums.ReleaseStatus.NOT_YET_RELEASED)
            throw new BusinessException("Este anime todavía no se estrenó. No se pueden agregar episodios.");
    }

    private void validateProgress(int progress, Integer totalUnits) {
        if (progress < 0) {
            throw new BusinessException("El progreso no puede ser negativo");
        }
        if (totalUnits != null && progress > totalUnits) {
            throw new BusinessException("El progreso no puede superar el total de episodios, capítulos o tomos");
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

    public LibraryImportResponse importFromAniList() {
        AppUser user=currentUser.require();
        return importForUser(user.getId(), user.getAnilistUsername());
    }

    public LibraryImportResponse importForUser(Long userId, String username) {
        ImportLock holder = importLocks.compute(userId, (ignored, current) -> {
            ImportLock selected = current == null ? new ImportLock() : current;
            selected.references++;
            return selected;
        });
        holder.lock.lock();
        try {
            AppUser profile = appUserRepository.findById(userId).orElseThrow();
            String accessToken = aniListAuthService.validToken(profile);
            if (accessToken == null || accessToken.isBlank()) {
                throw new BusinessException("La conexión con AniList venció. Vuelve a iniciar sesión.");
            }
            AniListLibraryExport externalLibrary = aniListClient.getUserLibrary(username.trim(), accessToken);
            List<AniListPersonFavorite> externalFavorites = aniListClient.getUserPersonFavorites(username.trim(), accessToken);
            return importWriter.apply(userId, externalLibrary, externalFavorites);
        } finally {
            holder.lock.unlock();
            importLocks.computeIfPresent(userId, (ignored, current) -> {
                current.references--;
                return current.references == 0 ? null : current;
            });
        }
    }

    private static final class ImportLock {
        private final ReentrantLock lock = new ReentrantLock();
        private int references;
    }

    private boolean isTotalProgress(LibraryEntry entry) {
        Integer totalUnits = entry.getMedia().getTotalUnits();
        return totalUnits != null && entry.getProgress().equals(totalUnits);
    }

    private void copyExternalMedia(AniListMediaResponse source, Media target) {
        target.setAnilistId(source.anilistId());
        target.setMediaType(source.mediaType());
        target.setMediaFormat(source.mediaFormat());
        target.setCountryOfOrigin(source.countryOfOrigin());
        target.setTitle(source.title());
        target.setRomajiTitle(source.romajiTitle());
        target.setCoverImage(source.coverImage());
        target.setBannerImage(source.bannerImage());
        target.setSynopsis(source.synopsis());
        target.setGenres(source.genres() == null ? null : String.join("|", source.genres()));
        target.setTags(source.tags() == null ? null : source.tags().stream().map(tag -> tag.name()).collect(java.util.stream.Collectors.joining("|")));
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
                media.getMediaFormat(),
                media.getCountryOfOrigin(),
                media.getTitle(),
                media.getRomajiTitle(),
                media.getCoverImage(),
                media.getBannerImage(),
                media.getSynopsis(),
                media.getGenres(),
                media.getTags(),
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
