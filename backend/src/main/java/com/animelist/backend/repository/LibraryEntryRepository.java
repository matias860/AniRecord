package com.animelist.backend.repository;

import com.animelist.backend.entity.LibraryEntry;
import com.animelist.backend.dto.library.LibraryEntrySummaryResponse;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.TrackingStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LibraryEntryRepository extends JpaRepository<LibraryEntry, Long> {

    @Query("""
            select new com.animelist.backend.dto.library.LibraryEntrySummaryResponse(
                entry.id, media.anilistId, media.mediaType, media.title, media.romajiTitle,
                media.coverImage, media.totalUnits, media.releaseStatus, media.releaseSeason,
                media.releaseYear, entry.progress, entry.personalScore, entry.rewatchCount,
                entry.trackingStatus, entry.startedAt, entry.completedAt, entry.createdAt, entry.updatedAt)
            from LibraryEntry entry join entry.media media
            where (:mediaType is null or media.mediaType = :mediaType)
              and (:status is null or entry.trackingStatus = :status)
            order by entry.updatedAt desc
            """)
    List<LibraryEntrySummaryResponse> findSummaries(
            @Param("mediaType") MediaType mediaType,
            @Param("status") TrackingStatus status
    );

    @Override
    @EntityGraph(attributePaths = "media")
    List<LibraryEntry> findAll();

    @EntityGraph(attributePaths = "media")
    Optional<LibraryEntry> findWithMediaById(Long id);

    boolean existsByMediaAnilistId(Integer anilistId);

    @EntityGraph(attributePaths = "media")
    Optional<LibraryEntry> findByMediaAnilistId(Integer anilistId);

    @EntityGraph(attributePaths = "media")
    List<LibraryEntry> findByMediaMediaType(MediaType mediaType);

    @EntityGraph(attributePaths = "media")
    List<LibraryEntry> findByTrackingStatus(TrackingStatus trackingStatus);

    @EntityGraph(attributePaths = "media")
    List<LibraryEntry> findByMediaMediaTypeAndTrackingStatus(
            MediaType mediaType,
            TrackingStatus trackingStatus
    );
}
