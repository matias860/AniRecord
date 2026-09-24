package com.animelist.backend.repository;

import com.animelist.backend.entity.TopNineSubmission;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopNineSubmissionRepository extends JpaRepository<TopNineSubmission, Long> {
    @EntityGraph(attributePaths = {"user", "selections", "selections.media"})
    List<TopNineSubmission> findDistinctByPeriodStartOrderByPublishedAtDesc(LocalDate periodStart);

    @EntityGraph(attributePaths = {"user", "selections", "selections.media"})
    Optional<TopNineSubmission> findByUserIdAndPeriodStart(Long userId, LocalDate periodStart);
}
