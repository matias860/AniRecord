package com.animelist.backend.service;

import com.animelist.backend.dto.topnine.PublishTopNineRequest;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.entity.LibraryEntry;
import com.animelist.backend.entity.Media;
import com.animelist.backend.entity.TopNineSubmission;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.TrackingStatus;
import com.animelist.backend.exception.BusinessException;
import com.animelist.backend.repository.LibraryEntryRepository;
import com.animelist.backend.repository.TopNineSubmissionRepository;
import com.animelist.backend.repository.UserProfileAssetRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TopNineServiceTest {
    private final TopNineSubmissionRepository submissions = mock(TopNineSubmissionRepository.class);
    private final LibraryEntryRepository library = mock(LibraryEntryRepository.class);
    private final UserProfileAssetRepository assets = mock(UserProfileAssetRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final AppUser user = new AppUser();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-23T15:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));
    private final TopNineService service = new TopNineService(submissions, library, assets, currentUser, clock);

    @BeforeEach void setup() {
        user.setId(7L);
        user.setDisplayName("Matías");
        when(currentUser.require()).thenReturn(user);
        when(assets.findUpdatedAt(anyLong(), any())).thenReturn(Optional.empty());
        when(submissions.findByUserIdAndPeriodStart(anyLong(), any())).thenReturn(Optional.empty());
        when(submissions.saveAndFlush(any())).thenAnswer(invocation -> {
            TopNineSubmission saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });
    }

    @Test void publishesNineDifferentOwnedEntriesInTheirChosenOrder() {
        List<Long> ids = new ArrayList<>();
        for (long id = 1; id <= 9; id++) {
            ids.add(id);
            when(library.findWithMediaByIdAndUserId(id, 7L)).thenReturn(Optional.of(entry(id, "Anime " + id, "Drama")));
        }

        var response = service.publish(new PublishTopNineRequest(ids));

        assertEquals(9, response.selections().size());
        assertEquals(1L, response.selections().get(0).libraryEntryId());
        assertEquals("Anime 9", response.selections().get(8).title());
        verify(submissions).saveAndFlush(argThat(saved -> saved.getSelections().size() == 9
                && saved.getSelections().get(0).getPosition() == 0
                && saved.getSelections().get(8).getPosition() == 8));
    }

    @Test void rejectsDuplicatesBeforeWritingAnything() {
        List<Long> ids = List.of(1L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);

        assertThrows(BusinessException.class, () -> service.publish(new PublishTopNineRequest(ids)));

        verifyNoInteractions(library);
        verify(submissions, never()).saveAndFlush(any());
    }

    @Test void rejectsPlannedEntriesWhenThePromptRequiresAStartedTitle() {
        List<Long> ids = new ArrayList<>();
        for (long id = 1; id <= 9; id++) {
            ids.add(id);
            LibraryEntry entry = entry(id, "Anime " + id, "Drama");
            if (id == 4) entry.setTrackingStatus(TrackingStatus.PLANNED);
            when(library.findWithMediaByIdAndUserId(id, 7L)).thenReturn(Optional.of(entry));
        }

        assertThrows(BusinessException.class, () -> service.publish(new PublishTopNineRequest(ids)));
        verify(submissions, never()).saveAndFlush(any());
    }

    @Test void limitsCommunityRowsInTheDatabaseAndLoadsAvatarsInOneBatch() {
        AppUser author = new AppUser(); author.setId(20L); author.setDisplayName("Autora");
        TopNineSubmission submission = new TopNineSubmission();
        submission.setId(30L); submission.setUser(author); submission.setPublishedAt(java.time.LocalDateTime.now(clock));
        when(currentUser.optional()).thenReturn(Optional.empty());
        when(submissions.findIdsByPeriodStartOrderByPublishedAtDesc(any(), any()))
                .thenReturn(List.of(30L));
        when(submissions.findDistinctByIdIn(List.of(30L))).thenReturn(List.of(submission));
        when(assets.findUpdatedAtByUserIds(List.of(20L), com.animelist.backend.enums.ProfileAssetType.AVATAR))
                .thenReturn(List.of());

        var response = service.current();

        assertEquals(1, response.submissions().size());
        verify(submissions).findIdsByPeriodStartOrderByPublishedAtDesc(any(), argThat(page ->
                page.getPageNumber() == 0 && page.getPageSize() == 24));
        verify(assets).findUpdatedAtByUserIds(List.of(20L), com.animelist.backend.enums.ProfileAssetType.AVATAR);
        verify(assets, never()).findUpdatedAt(anyLong(), any());
    }

    private LibraryEntry entry(long id, String title, String genres) {
        Media media = new Media();
        media.setId(id);
        media.setAnilistId((int) (1000 + id));
        media.setMediaType(MediaType.ANIME);
        media.setTitle(title);
        media.setGenres(genres);
        LibraryEntry entry = new LibraryEntry();
        entry.setId(id);
        entry.setUser(user);
        entry.setMedia(media);
        entry.setTrackingStatus(TrackingStatus.COMPLETED);
        return entry;
    }
}
