package com.animelist.backend.service;

import com.animelist.backend.dto.anilist.MediaRelationsResponse;
import com.animelist.backend.entity.Media;
import com.animelist.backend.repository.LibraryEntryRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MediaRelationWatchService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaRelationWatchService.class);
    private final LibraryEntryRepository libraryRepository;
    private final AniListClient aniListClient;
    private final MediaRelationPersistenceService persistenceService;
    private final boolean enabled;

    public MediaRelationWatchService(
            LibraryEntryRepository libraryRepository,
            AniListClient aniListClient,
            MediaRelationPersistenceService persistenceService,
            @Value("${app.relation-watch.enabled:true}") boolean enabled
    ) {
        this.libraryRepository = libraryRepository;
        this.aniListClient = aniListClient;
        this.persistenceService = persistenceService;
        this.enabled = enabled;
    }

    @Scheduled(
            initialDelayString = "${app.relation-watch.initial-delay:PT2M}",
            fixedDelayString = "${app.relation-watch.fixed-delay:PT12H}"
    )
    public void scan() {
        if (!enabled) return;
        List<Media> tracked = libraryRepository.findDistinctTrackedMedia();
        if (tracked.isEmpty()) return;

        try {
            Map<Integer, MediaRelationsResponse> relationsByMedia = aniListClient.getMediaRelations(
                            tracked.stream().map(Media::getAnilistId).distinct().toList())
                    .stream()
                    .collect(Collectors.toMap(MediaRelationsResponse::sourceAnilistId, Function.identity()));

            int notifications = 0;
            for (Media media : tracked) {
                MediaRelationsResponse response = relationsByMedia.get(media.getAnilistId());
                if (response != null) notifications += persistenceService.reconcile(media.getAnilistId(), response.relations());
            }
            LOGGER.info("Relation scan completed for {} media; {} local notifications created", tracked.size(), notifications);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not refresh media relations; the existing snapshot remains unchanged", exception);
        }
    }
}
