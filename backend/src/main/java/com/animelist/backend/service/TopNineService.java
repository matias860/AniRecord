package com.animelist.backend.service;

import com.animelist.backend.dto.topnine.CurrentTopNineResponse;
import com.animelist.backend.dto.topnine.PublishTopNineRequest;
import com.animelist.backend.dto.topnine.TopNineMediaResponse;
import com.animelist.backend.dto.topnine.TopNinePromptResponse;
import com.animelist.backend.dto.topnine.TopNineSubmissionResponse;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.entity.LibraryEntry;
import com.animelist.backend.entity.TopNineSubmission;
import com.animelist.backend.enums.ProfileAssetType;
import com.animelist.backend.exception.BusinessException;
import com.animelist.backend.exception.ResourceNotFoundException;
import com.animelist.backend.repository.LibraryEntryRepository;
import com.animelist.backend.repository.TopNineSubmissionRepository;
import com.animelist.backend.repository.UserProfileAssetRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.util.Comparator;

@Service
public class TopNineService {
    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final int MAX_COMMUNITY_SUBMISSIONS = 24;
    private final TopNineSubmissionRepository submissions;
    private final LibraryEntryRepository library;
    private final UserProfileAssetRepository assets;
    private final CurrentUserService currentUser;
    private final Clock clock;

    @Autowired
    public TopNineService(TopNineSubmissionRepository submissions, LibraryEntryRepository library,
                          UserProfileAssetRepository assets, CurrentUserService currentUser) {
        this(submissions, library, assets, currentUser, Clock.system(ARGENTINA));
    }

    TopNineService(TopNineSubmissionRepository submissions, LibraryEntryRepository library,
                   UserProfileAssetRepository assets, CurrentUserService currentUser, Clock clock) {
        this.submissions = submissions;
        this.library = library;
        this.assets = assets;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CurrentTopNineResponse current() {
        var period = period();
        AppUser viewer = currentUser.optional().orElse(null);
        Map<Integer, Long> ownEntries = viewer == null ? Map.of() : library.findIdentitiesByUserId(viewer.getId()).stream()
                .collect(Collectors.toMap(entry -> entry.anilistId(), entry -> entry.id(), (first, ignored) -> first));
        List<Long> publishedIds = submissions.findIdsByPeriodStartOrderByPublishedAtDesc(
                period.startsAt(), PageRequest.of(0, MAX_COMMUNITY_SUBMISSIONS));
        List<TopNineSubmission> publishedRows = publishedIds.isEmpty() ? List.of()
                : submissions.findDistinctByIdIn(publishedIds).stream()
                        .sorted(Comparator.comparing(TopNineSubmission::getPublishedAt).reversed()).toList();
        TopNineSubmission mineRow = viewer == null ? null : submissions
                .findByUserIdAndPeriodStart(viewer.getId(), period.startsAt()).orElse(null);
        List<Long> authorIds = java.util.stream.Stream.concat(publishedRows.stream(), java.util.stream.Stream.ofNullable(mineRow))
                .map(item -> item.getUser().getId()).distinct().toList();
        Map<Long, LocalDateTime> avatarUpdates = authorIds.isEmpty() ? Map.of()
                : assets.findUpdatedAtByUserIds(authorIds, ProfileAssetType.AVATAR).stream()
                        .collect(Collectors.toMap(row -> ((Number) row[0]).longValue(), row -> (LocalDateTime) row[1]));
        List<TopNineSubmissionResponse> published = publishedRows.stream()
                .map(item -> toResponse(item, viewer, ownEntries, avatarUpdates)).toList();
        TopNineSubmissionResponse mine = mineRow == null ? null : toResponse(mineRow, viewer, ownEntries, avatarUpdates);
        return new CurrentTopNineResponse(promptResponse(period), mine, published);
    }

    @Transactional
    public TopNineSubmissionResponse publish(PublishTopNineRequest request) {
        AppUser user = currentUser.require();
        var period = period();
        if (request.libraryEntryIds().size() != 9 || new HashSet<>(request.libraryEntryIds()).size() != 9) {
            throw new BusinessException("Elegí nueve obras diferentes para publicar tu 3×3.");
        }
        Map<Long, LibraryEntry> entries = request.libraryEntryIds().stream()
                .map(id -> library.findWithMediaByIdAndUserId(id, user.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Una de las obras ya no está en tu biblioteca.")))
                .collect(Collectors.toMap(LibraryEntry::getId, Function.identity()));
        for (LibraryEntry entry : entries.values()) {
            if (!period.prompt().matches(entry)) {
                throw new BusinessException("Una de las obras no cumple los requisitos de esta consigna.");
            }
        }

        submissions.findByUserIdAndPeriodStart(user.getId(), period.startsAt()).ifPresent(existing -> {
            submissions.delete(existing);
            submissions.flush();
        });
        TopNineSubmission submission = new TopNineSubmission();
        submission.setUser(user);
        submission.setPeriodStart(period.startsAt());
        submission.setPromptKey(period.prompt().key());
        submission.setPublishedAt(LocalDateTime.now(clock));
        for (int position = 0; position < request.libraryEntryIds().size(); position++) {
            submission.addSelection(entries.get(request.libraryEntryIds().get(position)).getMedia(), position);
        }
        try {
            TopNineSubmission saved = submissions.saveAndFlush(submission);
            Map<Integer, Long> ownEntries = entries.values().stream()
                    .collect(Collectors.toMap(entry -> entry.getMedia().getAnilistId(), LibraryEntry::getId));
            return toResponse(saved, user, ownEntries);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("No pudimos guardar el 3×3. Revisá las obras elegidas e intentá de nuevo.");
        }
    }

    private TopNinePromptCatalog.Period period() {
        return TopNinePromptCatalog.current(LocalDate.now(clock));
    }

    private TopNinePromptResponse promptResponse(TopNinePromptCatalog.Period period) {
        var prompt = period.prompt();
        return new TopNinePromptResponse(prompt.key(), prompt.title(), prompt.description(), period.startsAt(),
                period.changesAt(), prompt.mediaType(), prompt.genre(), prompt.format(), prompt.requiresStarted());
    }

    private TopNineSubmissionResponse toResponse(TopNineSubmission submission, AppUser viewer, Map<Integer, Long> ownEntries) {
        return toResponse(submission, viewer, ownEntries, null);
    }

    private TopNineSubmissionResponse toResponse(TopNineSubmission submission, AppUser viewer,
            Map<Integer, Long> ownEntries, Map<Long, LocalDateTime> avatarUpdates) {
        AppUser author = submission.getUser();
        boolean own = viewer != null && viewer.getId().equals(author.getId());
        java.util.Optional<LocalDateTime> avatarUpdated = avatarUpdates == null
                ? assets.findUpdatedAt(author.getId(), ProfileAssetType.AVATAR)
                : java.util.Optional.ofNullable(avatarUpdates.get(author.getId()));
        String avatar = avatarUpdated
                .map(updated -> "/api/profile/users/" + author.getId() + "/avatar?v="
                        + updated.atZone(ARGENTINA).toEpochSecond())
                .orElse(author.getAvatarUrl());
        List<TopNineMediaResponse> selections = submission.getSelections().stream().map(selection -> {
            var media = selection.getMedia();
            return new TopNineMediaResponse(own ? ownEntries.get(media.getAnilistId()) : null, selection.getPosition(),
                    media.getAnilistId(), media.getMediaType(), media.getTitle(), media.getRomajiTitle(), media.getCoverImage());
        }).toList();
        return new TopNineSubmissionResponse(submission.getId(), author.getId(), author.getDisplayName(), avatar,
                submission.getPublishedAt(), selections);
    }
}
