package com.animelist.backend.repository;

import com.animelist.backend.entity.AppNotification;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {
    boolean existsByAnilistNotificationId(Integer id);
    List<AppNotification> findAllByOrderByOccurredAtDesc(Pageable pageable);
    long countByReadFalse();
    @Modifying
    @Query("update AppNotification notification set notification.read = true where notification.read = false")
    int markAllAsRead();
}
