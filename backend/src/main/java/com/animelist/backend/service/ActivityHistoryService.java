package com.animelist.backend.service;

import com.animelist.backend.dto.history.ActivityHistoryResponse;
import com.animelist.backend.entity.ActivityHistory;
import com.animelist.backend.entity.LibraryEntry;
import com.animelist.backend.enums.ActivityType;
import com.animelist.backend.enums.TrackingStatus;
import com.animelist.backend.repository.ActivityHistoryRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityHistoryService {

    private final ActivityHistoryRepository repository;
    private final CurrentUserService currentUser;

    public ActivityHistoryService(ActivityHistoryRepository repository, CurrentUserService currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<ActivityHistoryResponse> findRecent(int limit) {
        return repository.findByUserIdOrderByCreatedAtDesc(currentUser.require().getId(), PageRequest.of(0, Math.min(limit, 100)))
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void record(ActivityType type, LibraryEntry entry, Integer previousProgress,
            TrackingStatus previousStatus, BigDecimal previousScore, Integer previousRewatchCount) {
        if (type == ActivityType.UPDATED
                && Objects.equals(previousProgress, entry.getProgress())
                && Objects.equals(previousStatus, entry.getTrackingStatus())
                && Objects.equals(previousScore, entry.getPersonalScore())
                && Objects.equals(previousRewatchCount, entry.getRewatchCount())) return;

        ActivityHistory activity = base(type, entry);
        activity.setPreviousProgress(previousProgress);
        activity.setNewProgress(entry.getProgress());
        activity.setPreviousStatus(previousStatus);
        activity.setNewStatus(entry.getTrackingStatus());
        activity.setPreviousScore(previousScore);
        activity.setNewScore(entry.getPersonalScore());
        activity.setPreviousRewatchCount(previousRewatchCount);
        activity.setNewRewatchCount(entry.getRewatchCount());
        repository.save(activity);
    }

    @Transactional
    public void recordRemoval(LibraryEntry entry) {
        ActivityHistory activity = base(ActivityType.REMOVED, entry);
        activity.setPreviousProgress(entry.getProgress());
        activity.setPreviousStatus(entry.getTrackingStatus());
        activity.setPreviousScore(entry.getPersonalScore());
        activity.setPreviousRewatchCount(entry.getRewatchCount());
        repository.save(activity);
    }

    private ActivityHistory base(ActivityType type, LibraryEntry entry) {
        ActivityHistory activity = new ActivityHistory();
        activity.setMedia(entry.getMedia());
        activity.setUser(entry.getUser());
        activity.setActivityType(type);
        return activity;
    }

    private ActivityHistoryResponse toResponse(ActivityHistory item) {
        return new ActivityHistoryResponse(item.getId(), item.getMedia().getAnilistId(),
                item.getMedia().getMediaType(), item.getMedia().getTitle(), item.getMedia().getCoverImage(),
                item.getActivityType(), item.getPreviousProgress(), item.getNewProgress(),
                item.getPreviousStatus(), item.getNewStatus(), item.getPreviousScore(), item.getNewScore(),
                item.getPreviousRewatchCount(), item.getNewRewatchCount(), item.getCreatedAt());
    }
}
