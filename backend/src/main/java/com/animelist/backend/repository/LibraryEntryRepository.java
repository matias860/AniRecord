package com.animelist.backend.repository;

import com.animelist.backend.entity.LibraryEntry;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.entity.Media;
import com.animelist.backend.dto.library.LibraryEntrySummaryResponse;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.AccountType;
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
                entry.id, media.anilistId, media.mediaType, media.mediaFormat, media.countryOfOrigin, media.title, media.romajiTitle,
                media.coverImage, media.genres, media.tags, media.totalUnits, media.releaseStatus, media.releaseSeason,
                media.releaseYear, entry.progress, entry.personalScore, entry.rewatchCount,
                entry.trackingStatus, entry.startedAt, entry.completedAt, entry.createdAt, entry.updatedAt)
            from LibraryEntry entry join entry.media media
            where entry.user.id = :userId
              and (:mediaType is null or media.mediaType = :mediaType)
              and (:status is null or entry.trackingStatus = :status)
            order by entry.updatedAt desc
            """)
    List<LibraryEntrySummaryResponse> findSummaries(
            @Param("userId") Long userId,
            @Param("mediaType") MediaType mediaType,
            @Param("status") TrackingStatus status
    );

    @Query("""
            select new com.animelist.backend.dto.library.LibraryEntrySummaryResponse(
                entry.id, media.anilistId, media.mediaType, media.mediaFormat, media.countryOfOrigin, media.title, media.romajiTitle,
                media.coverImage, media.genres, media.tags, media.totalUnits, media.releaseStatus, media.releaseSeason,
                media.releaseYear, entry.progress, entry.personalScore, entry.rewatchCount,
                entry.trackingStatus, entry.startedAt, entry.completedAt, entry.createdAt, entry.updatedAt)
            from LibraryEntry entry join entry.media media
            where entry.id = :entryId and entry.user.id = :userId
            """)
    Optional<LibraryEntrySummaryResponse> findSummaryByIdAndUserId(
            @Param("entryId") Long entryId,
            @Param("userId") Long userId
    );

    @EntityGraph(attributePaths = "media")
    Optional<LibraryEntry> findWithMediaByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndMediaAnilistId(Long userId, Integer anilistId);

    @EntityGraph(attributePaths = "media")
    Optional<LibraryEntry> findByUserIdAndMediaAnilistId(Long userId, Integer anilistId);

    @EntityGraph(attributePaths = "media")
    List<LibraryEntry> findByUserIdAndMediaMediaTypeAndTrackingStatus(
            Long userId, MediaType mediaType,
            TrackingStatus trackingStatus
    );

    @Query("select distinct entry.media from LibraryEntry entry where entry.user.accountType = :accountType")
    List<Media> findDistinctTrackedMediaByAccountType(@Param("accountType") AccountType accountType);

    @Query("select distinct entry.user from LibraryEntry entry where entry.media.id = :mediaId and entry.user.accountType = :accountType")
    List<AppUser> findDistinctUsersByMediaIdAndAccountType(
            @Param("mediaId") Long mediaId,
            @Param("accountType") AccountType accountType
    );
}
