package com.animelist.backend.service;

import com.animelist.backend.dto.recommendation.CreateRecommendationRequest;
import com.animelist.backend.dto.recommendation.WeeklyRecommendationResponse;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.entity.LibraryEntry;
import com.animelist.backend.entity.WeeklyRecommendation;
import com.animelist.backend.enums.ProfileAssetType;
import com.animelist.backend.exception.BusinessException;
import com.animelist.backend.exception.ResourceNotFoundException;
import com.animelist.backend.repository.LibraryEntryRepository;
import com.animelist.backend.repository.UserProfileAssetRepository;
import com.animelist.backend.repository.WeeklyRecommendationRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeeklyRecommendationService {
    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private final WeeklyRecommendationRepository recommendations;
    private final LibraryEntryRepository library;
    private final UserProfileAssetRepository assets;
    private final CurrentUserService currentUser;

    public WeeklyRecommendationService(
            WeeklyRecommendationRepository recommendations,
            LibraryEntryRepository library,
            UserProfileAssetRepository assets,
            CurrentUserService currentUser
    ) {
        this.recommendations = recommendations;
        this.library = library;
        this.assets = assets;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<WeeklyRecommendationResponse> currentWeek() {
        return recommendations.findByWeekStartOrderByCreatedAtDesc(currentWeekStart())
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<WeeklyRecommendationResponse> forMedia(Integer anilistId) {
        return recommendations.findByWeekStartAndMediaAnilistIdOrderByCreatedAtDesc(
                currentWeekStart(), anilistId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public WeeklyRecommendationResponse create(CreateRecommendationRequest request) {
        AppUser user = currentUser.require();
        LocalDate weekStart = currentWeekStart();
        if (recommendations.existsByUserIdAndWeekStart(user.getId(), weekStart)) {
            throw new BusinessException("Ya recomendaste una serie esta semana");
        }
        LibraryEntry entry = library.findWithMediaByIdAndUserId(request.libraryEntryId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("La serie no está en tu biblioteca"));
        WeeklyRecommendation recommendation = new WeeklyRecommendation();
        recommendation.setUser(user);
        recommendation.setMedia(entry.getMedia());
        recommendation.setWeekStart(weekStart);
        try {
            return toResponse(recommendations.saveAndFlush(recommendation));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("Ya recomendaste una serie esta semana");
        }
    }

    private LocalDate currentWeekStart() {
        return LocalDate.now(ARGENTINA).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private WeeklyRecommendationResponse toResponse(WeeklyRecommendation recommendation) {
        AppUser user = recommendation.getUser();
        var media = recommendation.getMedia();
        String avatar = assets.findUpdatedAt(user.getId(), ProfileAssetType.AVATAR)
                .map(updated -> "/api/profile/users/" + user.getId() + "/avatar?v="
                        + updated.atZone(ARGENTINA).toEpochSecond())
                .orElse(user.getAvatarUrl());
        return new WeeklyRecommendationResponse(
                recommendation.getId(), recommendation.getWeekStart(), recommendation.getCreatedAt(),
                user.getId(), user.getDisplayName(), avatar,
                media.getAnilistId(), media.getMediaType(), media.getTitle(), media.getRomajiTitle(),
                media.getCoverImage(), media.getBannerImage());
    }
}
