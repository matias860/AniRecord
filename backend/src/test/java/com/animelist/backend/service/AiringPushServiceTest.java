package com.animelist.backend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.animelist.backend.entity.Media;
import com.animelist.backend.repository.AppNotificationRepository;
import com.animelist.backend.repository.LibraryEntryRepository;
import com.animelist.backend.repository.MediaRepository;
import com.animelist.backend.repository.PushDeliveryRepository;
import com.animelist.backend.repository.PushSubscriptionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.support.TransactionTemplate;

class AiringPushServiceTest {
    @Test
    void schedulesTheNextKnownEpisodeAtItsActualAiringTime() {
        LibraryEntryRepository library = mock(LibraryEntryRepository.class);
        MediaRepository media = mock(MediaRepository.class);
        WebPushGateway gateway = mock(WebPushGateway.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        Instant airingAt = Instant.parse("2026-10-24T12:30:00Z");
        Media next = new Media();
        next.setNextAiringAt(airingAt);
        when(gateway.available()).thenReturn(true);
        when(library.findDistinctAiringTrackedMedia(any(), any())).thenReturn(List.of());
        when(media.findFirstByNextAiringAtIsNotNullOrderByNextAiringAtAsc()).thenReturn(Optional.of(next));
        when(scheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenReturn(mock(ScheduledFuture.class));

        AiringPushService service = new AiringPushService(library, media,
                mock(AppNotificationRepository.class), mock(PushSubscriptionRepository.class),
                mock(PushDeliveryRepository.class), mock(AniListClient.class), mock(TransactionTemplate.class),
                gateway, mock(PushDeliveryService.class), scheduler);

        service.refreshTrackedSchedules();

        verify(scheduler).schedule(any(Runnable.class), org.mockito.ArgumentMatchers.eq(airingAt));
    }
}
