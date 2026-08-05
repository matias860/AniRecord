package com.animelist.backend.repository;

import com.animelist.backend.entity.ActivityHistory;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityHistoryRepository extends JpaRepository<ActivityHistory, Long> {

    @EntityGraph(attributePaths = "media")
    List<ActivityHistory> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
