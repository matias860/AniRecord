package com.animelist.backend.repository;

import com.animelist.backend.entity.LibraryEntry;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.TrackingStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryEntryRepository extends JpaRepository<LibraryEntry, Long> {

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
