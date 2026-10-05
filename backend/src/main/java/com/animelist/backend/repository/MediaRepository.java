package com.animelist.backend.repository;

import com.animelist.backend.entity.Media;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface MediaRepository extends JpaRepository<Media, Long> {
    Optional<Media> findByAnilistId(Integer anilistId);
    List<Media> findAllByAnilistIdIn(Collection<Integer> anilistIds);
    List<Media> findByNextAiringAtLessThanEqualAndNextAiringEpisodeIsNotNull(Instant instant, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select media from Media media where media.id = :id")
    Optional<Media> findForUpdateById(@Param("id") Long id);
}
