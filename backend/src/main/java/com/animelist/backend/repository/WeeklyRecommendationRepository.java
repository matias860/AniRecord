package com.animelist.backend.repository;

import com.animelist.backend.entity.WeeklyRecommendation;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeeklyRecommendationRepository extends JpaRepository<WeeklyRecommendation, Long> {
    boolean existsByUserIdAndWeekStart(Long userId, LocalDate weekStart);

    @Query("select recommendation.user.id from WeeklyRecommendation recommendation where recommendation.weekStart = :weekStart and recommendation.user.id in :userIds")
    List<Long> findUserIdsByWeekStartAndUserIdIn(
            @Param("weekStart") LocalDate weekStart, @Param("userIds") List<Long> userIds);

    @EntityGraph(attributePaths = {"user", "media"})
    @Query("select recommendation.id from WeeklyRecommendation recommendation where recommendation.weekStart = :weekStart order by recommendation.createdAt desc")
    List<Long> findIdsByWeekStartOrderByCreatedAtDesc(@Param("weekStart") LocalDate weekStart, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "media"})
    List<WeeklyRecommendation> findDistinctByIdIn(List<Long> ids);

    @EntityGraph(attributePaths = {"user", "media"})
    List<WeeklyRecommendation> findByWeekStartAndMediaAnilistIdOrderByCreatedAtDesc(
            LocalDate weekStart, Integer anilistId, Pageable pageable);
}
