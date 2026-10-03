package com.animelist.backend.repository;

import com.animelist.backend.entity.AppNotification;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {
    boolean existsByUserIdAndAnilistNotificationId(Long userId, Integer id);
    boolean existsByUserIdAndSourceKey(Long userId, String sourceKey);
    List<AppNotification> findByUserIdOrderByOccurredAtDesc(Long userId, Pageable pageable);
    long countByUserIdAndReadFalse(Long userId);
    @Modifying
    @Query("update AppNotification notification set notification.read = true where notification.user.id = :userId and notification.read = false")
    int markAllAsRead(@org.springframework.data.repository.query.Param("userId") Long userId);
}
