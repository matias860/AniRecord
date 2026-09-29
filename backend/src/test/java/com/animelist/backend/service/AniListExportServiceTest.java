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
import static com.animelist.backend.service.AniListExportClient.Snapshot;
import static com.animelist.backend.service.AniListExportClient.ProgressUnit;

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
        when(entries.findSummaryByIdAndUserId(7L, 1L)).thenReturn(Optional.of(entry(TrackingStatus.COMPLETED, new BigDecimal("8.5"))));
        when(client.read("test-token", 42, MediaType.ANIME)).thenReturn(new Snapshot(Map.of(), "POINT_100"));
    }

    @Test void previewIsReadOnlyAndIgnoresRemoteOnlyEntries() {
        when(client.read("test-token", 42, MediaType.ANIME)).thenReturn(new Snapshot(Map.of(99, new Remote(999, new State("CURRENT", 2, 70))), "POINT_100"));
        var preview = service.preview();
        assertEquals(1, preview.changes().size()); assertNull(preview.changes().get(0).before());
        assertEquals(desired, preview.changes().get(0).after());
        verify(client, never()).save(any(), anyInt(), any(), any(), any());
    }

    @Test void unchangedEntriesAreNotExported() {
        when(client.read("test-token", 42, MediaType.ANIME)).thenReturn(new Snapshot(Map.of(10, new Remote(100, desired)), "POINT_100"));
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

    @Test void adaptsScoresToTheConnectedAniListAccountsScoringSystem() {
        var entry = entry(TrackingStatus.COMPLETED, new BigDecimal("8.5"));
        assertEquals(85, AniListExportService.desired(entry, "POINT_100").score());
        assertEquals(85, AniListExportService.desired(entry, "POINT_10_DECIMAL").score());
        assertEquals(90, AniListExportService.desired(entry, "POINT_10").score());
        assertEquals(80, AniListExportService.desired(entry, "POINT_5").score());
        assertEquals(85, AniListExportService.desired(entry, "POINT_3").score());
        assertEquals(0, AniListExportService.desired(entry(TrackingStatus.COMPLETED, null), "POINT_5").score());
    }

    @Test void sendsOnlyApprovedEntryAndRepeatedRequestsDoNotWriteAgain() {
        var preview = service.preview();
        when(client.readEntry("test-token", 42, 10, MediaType.ANIME)).thenReturn(null, new Remote(100, desired));
        service.apply(preview.id(), 7L); service.apply(preview.id(), 7L);
        verify(client, times(1)).save("test-token", 10, null, desired, ProgressUnit.EPISODES);
    }

    @Test void refusesChangesMadeOnAniListAfterPreview() {
        var preview = service.preview();
        when(client.readEntry("test-token", 42, 10, MediaType.ANIME)).thenReturn(new Remote(100, new State("CURRENT", 9, 60)));
        assertThrows(BusinessException.class, () -> service.apply(preview.id(), 7L));
        verify(client, never()).save(any(), anyInt(), any(), any(), any());
    }

    @Test void updatesExistingEntryUsingItsAniListEntryId() {
        State before = new State("CURRENT", 5, 60);
        State safelyMerged = new State("COMPLETED", 12, 60);
        when(client.read("test-token", 42, MediaType.ANIME)).thenReturn(new Snapshot(Map.of(10, new Remote(100, before)), "POINT_100"));
        var preview = service.preview();
        assertEquals(safelyMerged, preview.changes().get(0).after());
        when(client.readEntry("test-token", 42, 10, MediaType.ANIME)).thenReturn(new Remote(100, before));
        service.apply(preview.id(), 7L);
        verify(client).save("test-token", 10, 100, safelyMerged, ProgressUnit.EPISODES);
    }

    @Test void neverRegressesAniListProgressOrReplacesItsScore() {
        var local = entry(TrackingStatus.IN_PROGRESS, new BigDecimal("6.0"), 4, 12);
        State remote = new State("CURRENT", 10, 90);
        when(entries.findSummaries(1L, MediaType.ANIME, null)).thenReturn(List.of(local));
        when(client.read("test-token", 42, MediaType.ANIME)).thenReturn(new Snapshot(Map.of(10, new Remote(100, remote)), "POINT_100"));

        var preview = service.preview();

        assertTrue(preview.changes().isEmpty());
        assertEquals(1, preview.unchanged());
        verify(client, never()).save(any(), anyInt(), any(), any(), any());
    }

    @Test void advancesProgressAndFillsOnlyAnEmptyAniListScore() {
        var local = entry(TrackingStatus.IN_PROGRESS, new BigDecimal("8.5"), 8, 12);
        State remote = new State("CURRENT", 5, 0);
        when(entries.findSummaries(1L, MediaType.ANIME, null)).thenReturn(List.of(local));
        when(client.read("test-token", 42, MediaType.ANIME)).thenReturn(new Snapshot(Map.of(10, new Remote(100, remote)), "POINT_100"));

        var change = service.preview().changes().get(0);

        assertEquals(new State("CURRENT", 8, 85), change.after());
    }

    @Test void preservesExplicitAniListPausedAndDroppedStatuses() {
        State dropped = new State("DROPPED", 8, 70);
        when(client.read("test-token", 42, MediaType.ANIME)).thenReturn(new Snapshot(Map.of(10, new Remote(100, dropped)), "POINT_100"));

        var change = service.preview().changes().get(0);

        assertEquals(new State("DROPPED", 12, 70), change.after());
    }

    @Test void refusesChangesMadeLocallyAfterPreview() {
        var preview = service.preview();
        when(entries.findSummaryByIdAndUserId(7L, 1L)).thenReturn(Optional.of(entry(TrackingStatus.DROPPED, null)));
        assertThrows(BusinessException.class, () -> service.apply(preview.id(), 7L));
        verify(client, never()).save(any(), anyInt(), any(), any(), any());
    }

    @Test void cannotUseAnotherAccountsPreviewOrUnapprovedEntry() {
        var preview = service.preview();
        assertThrows(BusinessException.class, () -> service.apply(preview.id(), 999L));
        user.setId(2L);
        assertThrows(BusinessException.class, () -> service.apply(preview.id(), 7L));
        verify(client, never()).save(any(), anyInt(), any(), any(), any());
    }

    @Test void mangaNeverRegressesAniListProgressOrReplacesItsScore() {
        var local = entry(MediaType.MANGA, "MANGA", TrackingStatus.IN_PROGRESS, new BigDecimal("6.0"), 4, 20);
        State remote = new State("CURRENT", 10, 90);
        when(entries.findSummaries(1L, MediaType.MANGA, null)).thenReturn(List.of(local));
        when(client.read("test-token", 42, MediaType.MANGA)).thenReturn(new Snapshot(Map.of(10, new Remote(100, remote)), "POINT_100"));

        var preview = service.preview(MediaType.MANGA);

        assertEquals(MediaType.MANGA, preview.mediaType());
        assertTrue(preview.changes().isEmpty());
        assertEquals(1, preview.unchanged());
    }

    @Test void novelUsesVolumeProgressAndRechecksAniListBeforeWriting() {
        var local = entry(MediaType.MANGA, "NOVEL", TrackingStatus.IN_PROGRESS, new BigDecimal("8.5"), 3, 10);
        State before = new State("CURRENT", 1, 0);
        State after = new State("CURRENT", 3, 85);
        when(entries.findSummaries(1L, MediaType.MANGA, null)).thenReturn(List.of(local));
        when(entries.findSummaryByIdAndUserId(7L, 1L)).thenReturn(Optional.of(local));
        when(client.read("test-token", 42, MediaType.MANGA)).thenReturn(new Snapshot(Map.of(10, new Remote(100, before)), "POINT_100"));

        var preview = service.preview(MediaType.MANGA);
        assertEquals(ProgressUnit.VOLUMES, preview.changes().get(0).progressUnit());
        when(client.readEntry("test-token", 42, 10, MediaType.MANGA)).thenReturn(new Remote(100, before));

        service.apply(preview.id(), 7L);

        verify(client).save("test-token", 10, 100, after, ProgressUnit.VOLUMES);
    }

    @Test void mangaRefusesAChangeMadeOnAniListAfterPreview() {
        var local = entry(MediaType.MANGA, "MANGA", TrackingStatus.IN_PROGRESS, null, 8, 20);
        State before = new State("CURRENT", 4, 0);
        when(entries.findSummaries(1L, MediaType.MANGA, null)).thenReturn(List.of(local));
        when(client.read("test-token", 42, MediaType.MANGA)).thenReturn(new Snapshot(Map.of(10, new Remote(100, before)), "POINT_100"));
        var preview = service.preview(MediaType.MANGA);
        when(client.readEntry("test-token", 42, 10, MediaType.MANGA)).thenReturn(new Remote(100, new State("CURRENT", 9, 0)));

        assertThrows(BusinessException.class, () -> service.apply(preview.id(), 7L));
        verify(client, never()).save(any(), anyInt(), any(), any(), any());
    }

    @Test void expiredTokenAndLocalAccountsCannotExport() {
        when(auth.validToken(user)).thenReturn(null);
        assertThrows(BusinessException.class, service::preview);
        user.setAccountType(AccountType.LOCAL);
        assertThrows(BusinessException.class, service::preview);
        verifyNoInteractions(client);
    }

    private LibraryEntrySummaryResponse entry(TrackingStatus status, BigDecimal score) {
        return entry(status, score, 12, 12);
    }

    private LibraryEntrySummaryResponse entry(TrackingStatus status, BigDecimal score, int progress, Integer totalUnits) {
        return entry(MediaType.ANIME, "TV", status, score, progress, totalUnits);
    }

    private LibraryEntrySummaryResponse entry(MediaType type, String format, TrackingStatus status, BigDecimal score, int progress, Integer totalUnits) {
        return new LibraryEntrySummaryResponse(7L, 10, type, format, "JP", "Obra de prueba", "Test", null, null, null,
                totalUnits, ReleaseStatus.FINISHED, null, 2020, progress, score, 0, status, null, null, null, null);
    }
}

