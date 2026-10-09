package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.animelist.backend.dto.anilist.AniListLibraryExport;
import com.animelist.backend.dto.anilist.AniListLibraryItem;
import com.animelist.backend.dto.anilist.AniListMediaResponse;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.entity.LibraryEntry;
import com.animelist.backend.entity.Media;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.TrackingStatus;
import com.animelist.backend.repository.AppUserRepository;
import com.animelist.backend.repository.LibraryEntryRepository;
import com.animelist.backend.repository.MediaRepository;
import com.animelist.backend.repository.PersonFavoriteRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LibraryImportWriterTest {
    private final LibraryEntryRepository library = mock(LibraryEntryRepository.class);
    private final MediaRepository media = mock(MediaRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final PersonFavoriteRepository favorites = mock(PersonFavoriteRepository.class);
    private final LibraryImportWriter writer = new LibraryImportWriter(library, media, users, favorites);

    @Test
    void automaticImportPreservesExistingAniRecordTracking() {
        AppUser user = new AppUser();
        user.setId(1L);
        Media localMedia = new Media();
        localMedia.setAnilistId(10);
        localMedia.setMediaType(MediaType.ANIME);
        localMedia.setTitle("Anterior");
        LibraryEntry local = new LibraryEntry();
        local.setUser(user);
        local.setMedia(localMedia);
        local.setProgress(2);
        local.setPersonalScore(new BigDecimal("5.0"));
        local.setTrackingStatus(TrackingStatus.PLANNED);

        LocalDate started = LocalDate.of(2026, 10, 1);
        AniListLibraryItem remote = new AniListLibraryItem(media(10, "Actualizado"), 7,
                new BigDecimal("8.5"), 2, TrackingStatus.IN_PROGRESS, started, null);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(library.findByUserIdAndMediaAnilistId(1L, 10)).thenReturn(Optional.of(local));
        when(favorites.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        writer.apply(1L, new AniListLibraryExport("usuario", null, null, List.of(remote)), List.of());

        assertEquals("Actualizado", local.getMedia().getTitle());
        assertEquals(2, local.getProgress());
        assertEquals(new BigDecimal("5.0"), local.getPersonalScore());
        assertEquals(TrackingStatus.PLANNED, local.getTrackingStatus());
        verify(media).save(localMedia);
        verify(users).save(user);
    }

    @Test
    void explicitlySelectedEntryTakesCompletedStateFromAniList() {
        AppUser user = new AppUser();
        user.setId(1L);
        Media localMedia = new Media();
        localMedia.setAnilistId(10);
        localMedia.setMediaType(MediaType.ANIME);
        localMedia.setTitle("Clevatess");
        LibraryEntry local = new LibraryEntry();
        local.setUser(user);
        local.setMedia(localMedia);
        local.setProgress(7);
        local.setTrackingStatus(TrackingStatus.IN_PROGRESS);
        AniListLibraryItem remote = new AniListLibraryItem(media(10, "Clevatess"), 13,
                new BigDecimal("8.0"), 0, TrackingStatus.COMPLETED, null, LocalDate.of(2026, 10, 8));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(library.findForUpdateByUserIdAndMediaAnilistId(1L, 10)).thenReturn(Optional.of(local));

        writer.applyBatch(1L, List.of(remote), java.util.Map.of(10,
                new AniListImportService.LocalState(7, null, 0, TrackingStatus.IN_PROGRESS, null, null)));

        assertEquals(13, local.getProgress());
        assertEquals(TrackingStatus.COMPLETED, local.getTrackingStatus());
        assertEquals(new BigDecimal("8.0"), local.getPersonalScore());
        verify(library).save(local);
    }

    @Test
    void refreshesImportedOrderAndRetainsLocalOnlyFavorites() {
        AppUser user = new AppUser(); user.setId(1L);
        var imported = new com.animelist.backend.entity.PersonFavorite();
        imported.setAnilistId(20); imported.setPersonType(com.animelist.backend.enums.PersonType.CHARACTER);
        imported.setAnilistFavoriteOrder(8);
        var localOnly = new com.animelist.backend.entity.PersonFavorite();
        localOnly.setAnilistId(30); localOnly.setPersonType(com.animelist.backend.enums.PersonType.CHARACTER);
        localOnly.setAnilistFavoriteOrder(2);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(favorites.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(localOnly, imported));
        var remote = new com.animelist.backend.dto.anilist.AniListPersonFavorite(20,
                com.animelist.backend.enums.PersonType.CHARACTER, "Character", null, null, 0);
        var result = writer.complete(1L, new AniListLibraryExport("test", null, null, List.of()), List.of(remote));
        assertEquals(0, result.created());
        assertEquals(0, imported.getAnilistFavoriteOrder());
        assertEquals(null, localOnly.getAnilistFavoriteOrder());
        verify(favorites).save(imported);
    }

    private static AniListMediaResponse media(int id, String title) {
        return new AniListMediaResponse(id, MediaType.ANIME, "TV", "JP", title, title,
                "cover", null, null, List.of(), null, 12, null, null, null, null,
                2026, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), null);
    }
}
