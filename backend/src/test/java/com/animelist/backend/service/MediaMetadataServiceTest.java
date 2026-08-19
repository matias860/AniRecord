package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.animelist.backend.dto.anilist.AniListMediaResponse;
import com.animelist.backend.entity.Media;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.ReleaseSeason;
import com.animelist.backend.enums.ReleaseStatus;
import com.animelist.backend.repository.MediaRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaMetadataServiceTest {

    @Mock
    private MediaRepository mediaRepository;

    private MediaMetadataService service;

    @BeforeEach
    void setUp() {
        service = new MediaMetadataService(mediaRepository);
    }

    @Test
    void updatesOnlyExistingMediaWhenAniListPublishesAReleaseDate() {
        Media existing = storedMedia(null, ReleaseStatus.NOT_YET_RELEASED);
        AniListMediaResponse external = seasonMedia(LocalDate.of(2026, 10, 16), ReleaseStatus.RELEASING);
        when(mediaRepository.findAllByAnilistIdIn(anyCollection())).thenReturn(List.of(existing));

        int updated = service.refreshExistingSeasonMedia(List.of(external));

        assertEquals(1, updated);
        assertEquals(LocalDate.of(2026, 10, 16), existing.getReleaseStartDate());
        assertEquals(ReleaseStatus.RELEASING, existing.getReleaseStatus());
        assertNotNull(existing.getLastSyncedAt());
        verify(mediaRepository).saveAll(List.of(existing));
    }

    @Test
    void skipsDatabaseWritesWhenSeasonMetadataDidNotChange() {
        LocalDate releaseDate = LocalDate.of(2026, 10, 16);
        Media existing = storedMedia(releaseDate, ReleaseStatus.RELEASING);
        when(mediaRepository.findAllByAnilistIdIn(anyCollection())).thenReturn(List.of(existing));

        int updated = service.refreshExistingSeasonMedia(List.of(seasonMedia(releaseDate, ReleaseStatus.RELEASING)));

        assertEquals(0, updated);
        verify(mediaRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void doesNotCreateMediaThatIsNotAlreadyInTheLibraryDatabase() {
        when(mediaRepository.findAllByAnilistIdIn(anyCollection())).thenReturn(List.of());

        int updated = service.refreshExistingSeasonMedia(
                List.of(seasonMedia(LocalDate.of(2026, 10, 16), ReleaseStatus.RELEASING)));

        assertEquals(0, updated);
        verify(mediaRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    private Media storedMedia(LocalDate releaseDate, ReleaseStatus status) {
        Media media = new Media();
        media.setAnilistId(1234);
        media.setMediaType(MediaType.ANIME);
        media.setTitle("Serie de prueba");
        media.setTotalUnits(12);
        media.setReleaseStartDate(releaseDate);
        media.setReleaseStatus(status);
        media.setReleaseSeason(ReleaseSeason.FALL);
        media.setReleaseYear(2026);
        return media;
    }

    private AniListMediaResponse seasonMedia(LocalDate releaseDate, ReleaseStatus status) {
        return new AniListMediaResponse(
                1234,
                MediaType.ANIME,
                null,
                null,
                "Serie de prueba",
                null,
                null,
                null,
                null,
                null,
                12,
                releaseDate,
                null,
                status,
                ReleaseSeason.FALL,
                2026,
                100,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
