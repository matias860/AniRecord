package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;
import com.animelist.backend.dto.anilist.AniListLibraryExport;
import com.animelist.backend.dto.anilist.AniListLibraryItem;
import com.animelist.backend.dto.anilist.AniListMediaResponse;
import com.animelist.backend.dto.library.LibraryEntrySummaryResponse;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.enums.AccountType;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.TrackingStatus;
import com.animelist.backend.repository.LibraryEntryRepository;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AniListImportServiceTest {
    private final CurrentUserService current = mock(CurrentUserService.class);
    private final AniListAuthService auth = mock(AniListAuthService.class);
    private final AniListClient client = mock(AniListClient.class);
    private final LibraryEntryRepository entries = mock(LibraryEntryRepository.class);
    private final LibraryImportWriter writer = mock(LibraryImportWriter.class);
    private final AniListImportService service = new AniListImportService(current, auth, client, entries, writer);
    private final AppUser user = new AppUser();

    @BeforeEach
    void setup() {
        user.setId(1L); user.setAccountType(AccountType.ANILIST); user.setAnilistUserId(12); user.setAnilistUsername("test");
        when(current.require()).thenReturn(user);
        when(auth.validToken(user)).thenReturn("token");
        when(entries.findSummaries(1L, null, null)).thenReturn(List.of());
        when(client.getUserPersonFavorites("test", "token")).thenReturn(List.of());
    }

    @Test
    void importsLargeLibrariesInIdempotentBatchesBeforeCompleting() {
        List<AniListLibraryItem> remote = new ArrayList<>();
        for (int id = 1; id <= 21; id++) remote.add(item(id));
        AniListLibraryExport library = new AniListLibraryExport("test", null, null, remote);
        when(client.getUserLibrary("test", "token")).thenReturn(library);
        when(writer.applyBatch(eq(1L), any(), any())).thenAnswer(invocation -> {
            List<?> batch = invocation.getArgument(1);
            return new LibraryImportWriter.BatchResult(batch.size(), batch.size(), 0);
        });
        when(writer.complete(1L, library, List.of())).thenReturn(new LibraryImportWriter.FavoriteResult(0, 0, 0));

        var preview = service.preview();
        assertEquals(21, preview.changes().size());
        assertEquals(20, preview.batchSize());
        assertEquals(20, service.applyBatch(preview.id(), 0).processed());
        assertEquals(20, service.applyBatch(preview.id(), 0).processed());
        assertEquals(1, service.applyBatch(preview.id(), 1).processed());
        service.complete(preview.id());

        verify(writer, times(2)).applyBatch(eq(1L), any(), any());
        verify(writer).complete(1L, library, List.of());
    }

    @Test
    void previewOnlyShowsRealDifferencesAndRequiresExplicitExistingSelection() {
        AniListLibraryItem unchanged = item(1);
        AniListLibraryItem changed = new AniListLibraryItem(item(2).media(), 13,
                null, 0, TrackingStatus.COMPLETED, null, null);
        AniListLibraryExport remote = new AniListLibraryExport("test", null, null, List.of(unchanged, changed));
        when(client.getUserLibrary("test", "token")).thenReturn(remote);
        when(entries.findSummaries(1L, null, null)).thenReturn(List.of(summary(unchanged), summary(item(2))));
        when(writer.applyBatch(eq(1L), any(), any())).thenAnswer(invocation -> {
            List<?> selected = invocation.getArgument(1);
            return new LibraryImportWriter.BatchResult(selected.size(), 0, selected.size());
        });

        var preview = service.preview();

        assertEquals(1, preview.unchanged());
        assertEquals(1, preview.changes().size());
        assertEquals(TrackingStatus.IN_PROGRESS, preview.changes().get(0).before().status());
        assertEquals(TrackingStatus.COMPLETED, preview.changes().get(0).after().status());
        assertEquals(0, service.applyBatch(preview.id(), 0).updated());
        assertEquals(1, service.applySelection(preview.id(), List.of(2)).updated());
    }

    @Test
    void mergesAniListWithoutLosingBetterAniRecordData() {
        LocalDate localStarted = LocalDate.of(2026, 7, 1);
        AniListLibraryItem remote = new AniListLibraryItem(item(10).media(), 12,
                new BigDecimal("7.0"), 0, TrackingStatus.COMPLETED,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 8));
        var local = summary(new AniListLibraryItem(item(10).media(), 7,
                new BigDecimal("9.0"), 2, TrackingStatus.IN_PROGRESS, localStarted, null));
        when(client.getUserLibrary("test", "token"))
                .thenReturn(new AniListLibraryExport("test", null, null, List.of(remote)));
        when(entries.findSummaries(1L, null, null)).thenReturn(List.of(local));
        when(writer.applyBatch(eq(1L), any(), any()))
                .thenReturn(new LibraryImportWriter.BatchResult(1, 0, 1));

        var preview = service.preview();
        var change = preview.changes().get(0);

        assertEquals(12, change.after().progress());
        assertEquals(new BigDecimal("9.0"), change.after().score());
        assertEquals(2, change.after().repeats());
        assertEquals(TrackingStatus.COMPLETED, change.after().status());
        assertEquals(localStarted, change.after().startedAt());
        assertEquals(null, change.after().completedAt());

        service.applySelection(preview.id(), List.of(10));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AniListLibraryItem>> selected = ArgumentCaptor.forClass(List.class);
        verify(writer).applyBatch(eq(1L), selected.capture(), any());
        AniListLibraryItem applied = selected.getValue().get(0);
        assertEquals(12, applied.progress());
        assertEquals(new BigDecimal("9.0"), applied.personalScore());
        assertEquals(2, applied.rewatchCount());
        assertEquals(TrackingStatus.COMPLETED, applied.trackingStatus());
        assertEquals(localStarted, applied.startedAt());
        assertEquals(null, applied.completedAt());
    }

    @Test
    void ignoresRemoteValuesThatWouldMoveAniRecordBackwards() {
        AniListLibraryItem remote = new AniListLibraryItem(item(10).media(), 3,
                new BigDecimal("4.0"), 0, TrackingStatus.IN_PROGRESS, null, null);
        var local = summary(new AniListLibraryItem(item(10).media(), 8,
                new BigDecimal("8.5"), 1, TrackingStatus.PAUSED, null, null));
        when(client.getUserLibrary("test", "token"))
                .thenReturn(new AniListLibraryExport("test", null, null, List.of(remote)));
        when(entries.findSummaries(1L, null, null)).thenReturn(List.of(local));

        var preview = service.preview();

        assertEquals(0, preview.changes().size());
        assertEquals(1, preview.unchanged());
    }

    private static AniListLibraryItem item(int id) {
        AniListMediaResponse media = new AniListMediaResponse(id, MediaType.ANIME, "TV", "JP", "Anime " + id,
                "Anime " + id, "cover", null, null, List.of(), null, 12, null, null, null, null,
                2026, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), null, null);
        return new AniListLibraryItem(media, id % 12, null, 0, TrackingStatus.IN_PROGRESS, null, null);
    }

    private static LibraryEntrySummaryResponse summary(AniListLibraryItem item) {
        AniListMediaResponse media = item.media();
        return new LibraryEntrySummaryResponse((long) media.anilistId(), media.anilistId(), media.mediaType(),
                media.mediaFormat(), media.countryOfOrigin(), media.title(), media.romajiTitle(), media.coverImage(),
                null, null, media.totalUnits(), media.releaseStatus(), media.releaseSeason(), media.releaseYear(),
                item.progress(), item.personalScore(), item.rewatchCount(), item.trackingStatus(), item.startedAt(),
                item.completedAt(), null, null);
    }
}
