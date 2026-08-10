package com.animelist.backend.repository;

import com.animelist.backend.entity.WeeklyRecommendation;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyRecommendationRepository extends JpaRepository<WeeklyRecommendation, Long> {
    boolean existsByUserIdAndWeekStart(Long userId, LocalDate weekStart);

    @EntityGraph(attributePaths = {"user", "media"})
    List<WeeklyRecommendation> findByWeekStartOrderByCreatedAtDesc(LocalDate weekStart);

    @EntityGraph(attributePaths = {"user", "media"})
    List<WeeklyRecommendation> findByWeekStartAndMediaAnilistIdOrderByCreatedAtDesc(
            LocalDate weekStart, Integer anilistId);
}
