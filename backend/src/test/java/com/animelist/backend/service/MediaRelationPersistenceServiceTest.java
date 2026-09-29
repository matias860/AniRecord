package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.animelist.backend.dto.anilist.MediaRelationResponse;
import com.animelist.backend.entity.AppNotification;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.entity.Media;
import com.animelist.backend.entity.MediaRelationWatch;
import com.animelist.backend.enums.AccountType;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.repository.AppNotificationRepository;
import com.animelist.backend.repository.LibraryEntryRepository;
import com.animelist.backend.repository.MediaRelationSnapshotRepository;
import com.animelist.backend.repository.MediaRelationWatchRepository;
import com.animelist.backend.repository.MediaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaRelationPersistenceServiceTest {
    @Mock MediaRepository mediaRepository;
    @Mock MediaRelationWatchRepository watchRepository;
    @Mock MediaRelationSnapshotRepository snapshotRepository;
    @Mock LibraryEntryRepository libraryRepository;
    @Mock AppNotificationRepository notificationRepository;
    MediaRelationPersistenceService service;
    Media source;
    MediaRelationResponse sequel;

    @BeforeEach
    void setUp() {
        service = new MediaRelationPersistenceService(mediaRepository, watchRepository, snapshotRepository,
                libraryRepository, notificationRepository);
        source = new Media();
        source.setId(10L);
        source.setAnilistId(100);
        source.setTitle("Serie original");
        sequel = new MediaRelationResponse(200, "SEQUEL", MediaType.ANIME, "TV",
                "Temporada nueva", "https://example.com/cover.jpg");
        when(mediaRepository.findByAnilistId(100)).thenReturn(Optional.of(source));
        when(snapshotRepository.findBySourceMediaId(10L)).thenReturn(List.of());
    }

    @Test
    void firstScanCreatesSilentBaseline() {
        when(watchRepository.existsById(10L)).thenReturn(false);
        when(watchRepository.findById(10L)).thenReturn(Optional.empty());

        int created = service.reconcile(100, List.of(sequel));

        assertEquals(0, created);
        verify(snapshotRepository).saveAll(any());
        verify(watchRepository).save(any(MediaRelationWatch.class));
        verify(notificationRepository, never()).save(any());
        verify(libraryRepository, never()).findDistinctUsersByMediaIdAndAccountType(any(), any());
    }

    @Test
    void laterRelationNotifiesOnlyLocalOwnersOnce() {
        MediaRelationWatch watch = new MediaRelationWatch();
        watch.setMedia(source);
        when(watchRepository.existsById(10L)).thenReturn(true);
        when(watchRepository.findById(10L)).thenReturn(Optional.of(watch));

        AppUser local = user(1L, AccountType.LOCAL);
        when(libraryRepository.findDistinctUsersByMediaIdAndAccountType(10L, AccountType.LOCAL)).thenReturn(List.of(local));
        when(notificationRepository.existsByUserIdAndSourceKey(1L, "relation:100:200:SEQUEL")).thenReturn(false);

        int created = service.reconcile(100, List.of(sequel));

        assertEquals(1, created);
        ArgumentCaptor<AppNotification> notification = ArgumentCaptor.forClass(AppNotification.class);
        verify(notificationRepository).save(notification.capture());
        assertEquals(local, notification.getValue().getUser());
        assertEquals("relation:100:200:SEQUEL", notification.getValue().getSourceKey());
        assertEquals(200, notification.getValue().getAnilistMediaId());
        assertTrue(notification.getValue().getMessage().contains("secuela"));
    }

    private AppUser user(Long id, AccountType accountType) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setAccountType(accountType);
        return user;
    }
}
