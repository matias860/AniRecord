package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.animelist.backend.dto.anilist.AniListLibraryExport;
import com.animelist.backend.dto.anilist.AniListLibraryItem;
import com.animelist.backend.dto.anilist.AniListMediaResponse;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.enums.AccountType;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.TrackingStatus;
import com.animelist.backend.repository.LibraryEntryRepository;
import java.util.ArrayList;
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

    private static AniListLibraryItem item(int id) {
        AniListMediaResponse media = new AniListMediaResponse(id, MediaType.ANIME, "TV", "JP", "Anime " + id,
                "Anime " + id, "cover", null, null, List.of(), null, 12, null, null, null, null,
                2026, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), null);
        return new AniListLibraryItem(media, id % 12, null, 0, TrackingStatus.IN_PROGRESS, null, null);
    }
}
