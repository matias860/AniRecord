package com.animelist.backend.repository;

import com.animelist.backend.entity.AppNotification;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {
    boolean existsByAnilistNotificationId(Integer id);
    List<AppNotification> findAllByOrderByOccurredAtDesc(Pageable pageable);
    long countByReadFalse();
}
