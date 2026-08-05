package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.animelist.backend.dto.library.AddLibraryEntryRequest;
import com.animelist.backend.dto.library.LibraryEntryResponse;
import com.animelist.backend.dto.library.UpdateLibraryEntryRequest;
import com.animelist.backend.entity.LibraryEntry;
import com.animelist.backend.entity.Media;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.TrackingStatus;
import com.animelist.backend.exception.BusinessException;
import com.animelist.backend.repository.LibraryEntryRepository;
import com.animelist.backend.repository.MediaRepository;
import com.animelist.backend.repository.AppUserRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LibraryServiceTest {

    @Mock
    private LibraryEntryRepository libraryEntryRepository;

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private AniListClient aniListClient;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private ActivityHistoryService activityHistoryService;

    private LibraryService libraryService;

    @BeforeEach
    void setUp() {
        libraryService = new LibraryService(
                libraryEntryRepository, mediaRepository, aniListClient, appUserRepository, activityHistoryService
        );
        lenient().when(libraryEntryRepository.save(any(LibraryEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void incrementingLastUnitCompletesEntry() {
        LibraryEntry entry = entry(11, 12, TrackingStatus.IN_PROGRESS);
        mockEntry(1L, entry);

        LibraryEntryResponse response = libraryService.incrementProgress(1L);

        assertEquals(12, response.progress());
        assertEquals(TrackingStatus.COMPLETED, response.trackingStatus());
        assertNotNull(response.completedAt());
    }

    @Test
    void completedStatusSetsKnownTotalAsProgress() {
        LibraryEntry entry = entry(4, 12, TrackingStatus.IN_PROGRESS);
        mockEntry(1L, entry);
        UpdateLibraryEntryRequest request = new UpdateLibraryEntryRequest(
                null,
                null,
                null,
                TrackingStatus.COMPLETED,
                null,
                null,
                null
        );

        LibraryEntryResponse response = libraryService.update(1L, request);

        assertEquals(12, response.progress());
        assertEquals(TrackingStatus.COMPLETED, response.trackingStatus());
        assertNotNull(response.completedAt());
    }

    @Test
    void leavingCompletedStatusClearsCompletionDate() {
        LibraryEntry entry = entry(12, 12, TrackingStatus.COMPLETED);
        entry.setCompletedAt(LocalDate.now().minusDays(1));
        mockEntry(1L, entry);
        UpdateLibraryEntryRequest request = new UpdateLibraryEntryRequest(
                8,
                null,
                null,
                TrackingStatus.IN_PROGRESS,
                null,
                null,
                null
        );

        LibraryEntryResponse response = libraryService.update(1L, request);

        assertEquals(8, response.progress());
        assertEquals(TrackingStatus.IN_PROGRESS, response.trackingStatus());
        assertNull(response.completedAt());
    }

    @Test
    void loweringProgressWithoutStatusReopensCompletedEntry() {
        LibraryEntry entry = entry(12, 12, TrackingStatus.COMPLETED);
        entry.setCompletedAt(LocalDate.now());
        mockEntry(1L, entry);
        UpdateLibraryEntryRequest request = new UpdateLibraryEntryRequest(
                10,
                null,
                null,
                null,
                null,
                null,
                null
        );

        LibraryEntryResponse response = libraryService.update(1L, request);

        assertEquals(10, response.progress());
        assertEquals(TrackingStatus.IN_PROGRESS, response.trackingStatus());
        assertNull(response.completedAt());
    }

    @Test
    void plannedStatusResetsProgressAndDates() {
        LibraryEntry entry = entry(6, 12, TrackingStatus.IN_PROGRESS);
        entry.setStartedAt(LocalDate.now().minusDays(10));
        mockEntry(1L, entry);
        UpdateLibraryEntryRequest request = new UpdateLibraryEntryRequest(
                null,
                null,
                null,
                TrackingStatus.PLANNED,
                null,
                null,
                null
        );

        LibraryEntryResponse response = libraryService.update(1L, request);

        assertEquals(0, response.progress());
        assertEquals(TrackingStatus.PLANNED, response.trackingStatus());
        assertNull(response.startedAt());
        assertNull(response.completedAt());
    }

    @Test
    void duplicateAniListIdIsRejectedBeforeCallingAniList() {
        when(libraryEntryRepository.existsByMediaAnilistId(21)).thenReturn(true);

        assertThrows(
                BusinessException.class,
                () -> libraryService.add(new AddLibraryEntryRequest(21, TrackingStatus.PLANNED))
        );
        verifyNoInteractions(aniListClient);
    }

    private void mockEntry(Long id, LibraryEntry entry) {
        entry.setId(id);
        when(libraryEntryRepository.findWithMediaById(id)).thenReturn(Optional.of(entry));
    }

    private LibraryEntry entry(int progress, Integer totalUnits, TrackingStatus status) {
        Media media = new Media();
        media.setAnilistId(21);
        media.setMediaType(MediaType.ANIME);
        media.setTitle("One Piece");
        media.setTotalUnits(totalUnits);

        LibraryEntry entry = new LibraryEntry();
        entry.setMedia(media);
        entry.setProgress(progress);
        entry.setTrackingStatus(status);
        return entry;
    }
}
