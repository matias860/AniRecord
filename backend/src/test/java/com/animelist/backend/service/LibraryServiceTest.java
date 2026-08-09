package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.animelist.backend.dto.library.AddLibraryEntryRequest;
import com.animelist.backend.dto.library.LibraryEntryResponse;
import com.animelist.backend.dto.library.UpdateLibraryEntryRequest;
import com.animelist.backend.dto.library.LibraryImportResponse;
import com.animelist.backend.dto.anilist.AniListLibraryExport;
import com.animelist.backend.dto.anilist.AniListPersonFavorite;
import com.animelist.backend.entity.LibraryEntry;
import com.animelist.backend.entity.Media;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.entity.PersonFavorite;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.TrackingStatus;
import com.animelist.backend.enums.PersonType;
import com.animelist.backend.exception.BusinessException;
import com.animelist.backend.repository.LibraryEntryRepository;
import com.animelist.backend.repository.MediaRepository;
import com.animelist.backend.repository.AppUserRepository;
import com.animelist.backend.repository.PersonFavoriteRepository;
import java.time.LocalDate;
import java.util.List;
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
    @Mock private CurrentUserService currentUserService;
    @Mock private AniListAuthService aniListAuthService;
    @Mock private PersonFavoriteRepository personFavoriteRepository;
    private AppUser user;

    private LibraryService libraryService;

    @BeforeEach
    void setUp() {
        libraryService = new LibraryService(
                libraryEntryRepository, mediaRepository, aniListClient, appUserRepository, activityHistoryService,
                currentUserService, aniListAuthService, personFavoriteRepository
        );
        user = new AppUser(); user.setId(1L); user.setAnilistUserId(1); user.setAnilistUsername("test");
        lenient().when(currentUserService.require()).thenReturn(user);
        lenient().when(libraryEntryRepository.save(any(LibraryEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void loadingLibraryOnlyReadsLocalData() {
        when(libraryEntryRepository.findSummaries(1L, null, null)).thenReturn(List.of());

        assertEquals(List.of(), libraryService.findAll(null, null));

        verifyNoInteractions(aniListClient, aniListAuthService);
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
        when(libraryEntryRepository.existsByUserIdAndMediaAnilistId(1L, 21)).thenReturn(true);

        assertThrows(
                BusinessException.class,
                () -> libraryService.add(new AddLibraryEntryRequest(21, TrackingStatus.PLANNED))
        );
        verifyNoInteractions(aniListClient);
    }

    @Test
    void importingAniListAddsNewPersonFavoritesWithoutDuplicatingLocalOnes() {
        PersonFavorite existing = new PersonFavorite();
        existing.setAnilistId(40);
        existing.setPersonType(PersonType.CHARACTER);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(aniListAuthService.validToken(user)).thenReturn("token");
        when(aniListClient.getUserLibrary("test", "token"))
                .thenReturn(new AniListLibraryExport("test", null, null, List.of()));
        when(aniListClient.getUserPersonFavorites("test", "token")).thenReturn(List.of(
                new AniListPersonFavorite(40, PersonType.CHARACTER, "Luffy", null, "character.jpg"),
                new AniListPersonFavorite(95075, PersonType.STAFF, "Mayumi Tanaka", null, "staff.jpg")
        ));
        when(personFavoriteRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(existing));

        LibraryImportResponse response = libraryService.importForUser(1L, "test");

        assertEquals(1, response.personFavoritesCreated());
        assertEquals(0, response.characterFavoritesCreated());
        assertEquals(1, response.staffFavoritesCreated());
        verify(personFavoriteRepository, times(1)).save(any(PersonFavorite.class));
    }

    private void mockEntry(Long id, LibraryEntry entry) {
        entry.setId(id);
        when(libraryEntryRepository.findWithMediaByIdAndUserId(id, 1L)).thenReturn(Optional.of(entry));
    }

    private LibraryEntry entry(int progress, Integer totalUnits, TrackingStatus status) {
        Media media = new Media();
        media.setAnilistId(21);
        media.setMediaType(MediaType.ANIME);
        media.setTitle("One Piece");
        media.setTotalUnits(totalUnits);

        LibraryEntry entry = new LibraryEntry();
        entry.setMedia(media);
        entry.setUser(user);
        entry.setProgress(progress);
        entry.setTrackingStatus(status);
        return entry;
    }
}
