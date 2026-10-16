package com.animelist.backend.repository;

import com.animelist.backend.entity.TopNineSubmission;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface TopNineSubmissionRepository extends JpaRepository<TopNineSubmission, Long> {
    boolean existsByUserIdAndPeriodStart(Long userId, LocalDate periodStart);

    @Query("select submission.id from TopNineSubmission submission where submission.periodStart = :periodStart order by submission.publishedAt desc")
    List<Long> findIdsByPeriodStartOrderByPublishedAtDesc(LocalDate periodStart, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "selections", "selections.media"})
    List<TopNineSubmission> findDistinctByIdIn(List<Long> ids);

    @EntityGraph(attributePaths = {"user", "selections", "selections.media"})
    Optional<TopNineSubmission> findByUserIdAndPeriodStart(Long userId, LocalDate periodStart);
}
