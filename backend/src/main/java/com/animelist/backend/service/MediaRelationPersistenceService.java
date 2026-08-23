package com.animelist.backend.service;

import com.animelist.backend.dto.anilist.MediaRelationResponse;
import com.animelist.backend.entity.AppNotification;
import com.animelist.backend.entity.Media;
import com.animelist.backend.entity.MediaRelationSnapshot;
import com.animelist.backend.entity.MediaRelationWatch;
import com.animelist.backend.enums.AccountType;
import com.animelist.backend.enums.NotificationType;
import com.animelist.backend.repository.AppNotificationRepository;
import com.animelist.backend.repository.LibraryEntryRepository;
import com.animelist.backend.repository.MediaRelationSnapshotRepository;
import com.animelist.backend.repository.MediaRelationWatchRepository;
import com.animelist.backend.repository.MediaRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaRelationPersistenceService {
    private final MediaRepository mediaRepository;
    private final MediaRelationWatchRepository watchRepository;
    private final MediaRelationSnapshotRepository snapshotRepository;
    private final LibraryEntryRepository libraryRepository;
    private final AppNotificationRepository notificationRepository;

    public MediaRelationPersistenceService(
            MediaRepository mediaRepository,
            MediaRelationWatchRepository watchRepository,
            MediaRelationSnapshotRepository snapshotRepository,
            LibraryEntryRepository libraryRepository,
            AppNotificationRepository notificationRepository
    ) {
        this.mediaRepository = mediaRepository;
        this.watchRepository = watchRepository;
        this.snapshotRepository = snapshotRepository;
        this.libraryRepository = libraryRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public int reconcile(Integer sourceAnilistId, List<MediaRelationResponse> currentRelations) {
        Media source = mediaRepository.findByAnilistId(sourceAnilistId).orElse(null);
        if (source == null) return 0;

        boolean initialized = watchRepository.existsById(source.getId());
        Set<String> known = new HashSet<>();
        snapshotRepository.findBySourceMediaId(source.getId())
                .forEach(item -> known.add(relationKey(item.getRelatedAnilistId(), item.getRelationType())));

        Map<String, MediaRelationResponse> uniqueCurrent = new LinkedHashMap<>();
        safeRelations(currentRelations).stream()
                .filter(this::validRelation)
                .forEach(relation -> uniqueCurrent.putIfAbsent(
                        relationKey(relation.anilistId(), relation.relationType()), relation));

        List<MediaRelationResponse> additions = uniqueCurrent.values().stream()
                .filter(relation -> !known.contains(relationKey(relation.anilistId(), relation.relationType())))
                .toList();

        if (!additions.isEmpty()) {
            snapshotRepository.saveAll(additions.stream().map(relation -> snapshot(source, relation)).toList());
        }

        MediaRelationWatch watch = watchRepository.findById(source.getId()).orElseGet(() -> {
            MediaRelationWatch created = new MediaRelationWatch();
            created.setMedia(source);
            return created;
        });
        watch.setLastCheckedAt(LocalDateTime.now());
        watchRepository.save(watch);

        if (!initialized || additions.isEmpty()) return 0;
        return notifyLocalOwners(source, additions);
    }

    private int notifyLocalOwners(Media source, List<MediaRelationResponse> additions) {
        int created = 0;
        var localOwners = libraryRepository.findDistinctUsersByMediaId(source.getId()).stream()
                .filter(user -> user.getAccountType() == AccountType.LOCAL)
                .toList();

        for (MediaRelationResponse relation : additions) {
            String sourceKey = "relation:%d:%d:%s".formatted(
                    source.getAnilistId(), relation.anilistId(), relation.relationType());
            for (var user : localOwners) {
                if (notificationRepository.existsByUserIdAndSourceKey(user.getId(), sourceKey)) continue;
                AppNotification notification = new AppNotification();
                notification.setUser(user);
                notification.setSourceKey(sourceKey);
                notification.setNotificationType(NotificationType.RELATED_MEDIA_ADDITION);
                notification.setAnilistMediaId(relation.anilistId());
                notification.setTitle(relation.title());
                notification.setCoverImage(relation.coverImage());
                notification.setMessage(message(source.getTitle(), relation.relationType()));
                notification.setRead(false);
                notification.setOccurredAt(LocalDateTime.now());
                notificationRepository.save(notification);
                created++;
            }
        }
        return created;
    }

    private MediaRelationSnapshot snapshot(Media source, MediaRelationResponse relation) {
        MediaRelationSnapshot snapshot = new MediaRelationSnapshot();
        snapshot.setSourceMedia(source);
        snapshot.setRelatedAnilistId(relation.anilistId());
        snapshot.setRelationType(relation.relationType());
        snapshot.setRelatedMediaType(relation.mediaType());
        snapshot.setRelatedFormat(relation.format());
        snapshot.setRelatedTitle(relation.title());
        snapshot.setRelatedCoverImage(relation.coverImage());
        return snapshot;
    }

    private String message(String sourceTitle, String relationType) {
        return switch (relationType) {
            case "SEQUEL" -> "Se anunció una nueva secuela de " + sourceTitle;
            case "PREQUEL" -> "Se agregó una nueva precuela relacionada con " + sourceTitle;
            case "ADAPTATION" -> "Se agregó una nueva adaptación de " + sourceTitle;
            case "SOURCE" -> "Se agregó el material original de " + sourceTitle;
            case "SPIN_OFF" -> "Se anunció un nuevo spin-off de " + sourceTitle;
            default -> "Se agregó una nueva obra relacionada con " + sourceTitle;
        };
    }

    private boolean validRelation(MediaRelationResponse relation) {
        return relation != null && relation.anilistId() != null && relation.mediaType() != null
                && relation.relationType() != null && !relation.relationType().isBlank()
                && relation.title() != null && !relation.title().isBlank();
    }

    private List<MediaRelationResponse> safeRelations(List<MediaRelationResponse> relations) {
        return Objects.requireNonNullElse(relations, List.of());
    }

    private String relationKey(Integer relatedId, String relationType) {
        return relatedId + ":" + relationType;
    }
}
