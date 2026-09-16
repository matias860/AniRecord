package com.animelist.backend.service;

import com.animelist.backend.dto.library.LibraryEntrySummaryResponse;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.enums.*;
import com.animelist.backend.exception.BusinessException;
import com.animelist.backend.repository.LibraryEntryRepository;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.animelist.backend.service.AniListExportClient.State;
import static com.animelist.backend.service.AniListExportClient.Remote;

class AniListExportServiceTest {
    final CurrentUserService current = mock(CurrentUserService.class);
    final AniListAuthService auth = mock(AniListAuthService.class);
    final LibraryEntryRepository entries = mock(LibraryEntryRepository.class);
    final AniListExportClient client = mock(AniListExportClient.class);
    final AniListExportService service = new AniListExportService(current, auth, entries, client);
    final AppUser user = new AppUser();
    final State desired = new State("COMPLETED", 12, 85);

    @BeforeEach void setup() {
        user.setId(1L); user.setAccountType(AccountType.ANILIST); user.setAnilistUserId(42); user.setAnilistUsername("test");
        when(current.require()).thenReturn(user); when(auth.validToken(user)).thenReturn("test-token");
        when(entries.findSummaries(1L, MediaType.ANIME, null)).thenReturn(List.of(entry(TrackingStatus.COMPLETED, new BigDecimal("8.5"))));
        when(client.read("test-token", 42)).thenReturn(Map.of());
    }

    @Test void previewIsReadOnlyAndIgnoresRemoteOnlyEntries() {
        when(client.read("test-token", 42)).thenReturn(Map.of(99, new Remote(999, new State("CURRENT", 2, 70))));
        var preview = service.preview();
        assertEquals(1, preview.changes().size()); assertNull(preview.changes().get(0).before());
        assertEquals(desired, preview.changes().get(0).after());
        verify(client, never()).save(any(), anyInt(), any(), any());
    }

    @Test void unchangedEntriesAreNotExported() {
        when(client.read("test-token", 42)).thenReturn(Map.of(10, new Remote(100, desired)));
        var preview = service.preview(); assertTrue(preview.changes().isEmpty()); assertEquals(1, preview.unchanged());
    }

    @Test void mapsAllFiveStatusesAndClearsMissingScoreExplicitly() {
        Map<TrackingStatus, String> mapping = Map.of(TrackingStatus.IN_PROGRESS,"CURRENT", TrackingStatus.COMPLETED,"COMPLETED",
                TrackingStatus.PLANNED,"PLANNING", TrackingStatus.PAUSED,"PAUSED", TrackingStatus.DROPPED,"DROPPED");
        mapping.forEach((local, remote) -> {
            State result = AniListExportService.desired(entry(local, null));
            assertEquals(remote, result.status()); assertEquals(0, result.score());
        });
        assertEquals(88, AniListExportService.desired(entry(TrackingStatus.COMPLETED, new BigDecimal("8.75"))).score());
    }

    @Test void sendsOnlyApprovedEntryAndRepeatedRequestsDoNotWriteAgain() {
        var preview = service.preview();
        when(client.readEntry("test-token", 42, 10)).thenReturn(null, new Remote(100, desired));
        service.apply(preview.id(), 7L); service.apply(preview.id(), 7L);
        verify(client, times(1)).save("test-token", 10, null, desired);
    }

    @Test void refusesChangesMadeOnAniListAfterPreview() {
        var preview = service.preview();
        when(client.readEntry("test-token", 42, 10)).thenReturn(new Remote(100, new State("CURRENT", 9, 60)));
        assertThrows(BusinessException.class, () -> service.apply(preview.id(), 7L));
        verify(client, never()).save(any(), anyInt(), any(), any());
    }

    @Test void updatesExistingEntryUsingItsAniListEntryId() {
        State before = new State("CURRENT", 5, 60);
        when(client.read("test-token", 42)).thenReturn(Map.of(10, new Remote(100, before)));
        var preview = service.preview();
        when(client.readEntry("test-token", 42, 10)).thenReturn(new Remote(100, before));
        service.apply(preview.id(), 7L);
        verify(client).save("test-token", 10, 100, desired);
    }

    @Test void refusesChangesMadeLocallyAfterPreview() {
        var preview = service.preview();
        when(entries.findSummaries(1L, MediaType.ANIME, null)).thenReturn(List.of(entry(TrackingStatus.DROPPED, null)));
        assertThrows(BusinessException.class, () -> service.apply(preview.id(), 7L));
        verify(client, never()).save(any(), anyInt(), any(), any());
    }

    @Test void cannotUseAnotherAccountsPreviewOrUnapprovedEntry() {
        var preview = service.preview();
        assertThrows(BusinessException.class, () -> service.apply(preview.id(), 999L));
        user.setId(2L);
        assertThrows(BusinessException.class, () -> service.apply(preview.id(), 7L));
        verify(client, never()).save(any(), anyInt(), any(), any());
    }

    @Test void expiredTokenAndLocalAccountsCannotExport() {
        when(auth.validToken(user)).thenReturn(null);
        assertThrows(BusinessException.class, service::preview);
        user.setAccountType(AccountType.LOCAL);
        assertThrows(BusinessException.class, service::preview);
        verifyNoInteractions(client);
    }

    private LibraryEntrySummaryResponse entry(TrackingStatus status, BigDecimal score) {
        return new LibraryEntrySummaryResponse(7L, 10, MediaType.ANIME, "TV", "JP", "Anime de prueba", "Anime", null, null, null,
                12, ReleaseStatus.FINISHED, null, 2020, 12, score, 0, status, null, null, null, null);
    }
}

