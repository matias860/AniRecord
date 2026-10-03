package com.animelist.backend.repository;

import com.animelist.backend.dto.notification.NotificationResponse;
import com.animelist.backend.entity.AppNotification;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {
    boolean existsByUserIdAndAnilistNotificationId(Long userId, Integer id);
    boolean existsByUserIdAndSourceKey(Long userId, String sourceKey);
    /**
     * Only select fields required by the UI. This keeps notification reads working
     * while an additive schema migration is still being applied in production.
     */
    @Query("""
        select new com.animelist.backend.dto.notification.NotificationResponse(
            notification.id, notification.notificationType, notification.anilistMediaId,
            notification.title, notification.coverImage, notification.message,
            notification.episode, coalesce(notification.read, false), notification.occurredAt
        )
        from AppNotification notification
        where notification.user.id = :userId
        order by notification.occurredAt desc
        """)
    List<NotificationResponse> findResponsesByUserId(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            Pageable pageable);
    long countByUserIdAndReadFalse(Long userId);
    @Modifying
    @Query("update AppNotification notification set notification.read = true where notification.user.id = :userId and notification.read = false")
    int markAllAsRead(@org.springframework.data.repository.query.Param("userId") Long userId);
    @Modifying
    @Query("update AppNotification notification set notification.read = true where notification.user.id = :userId and notification.id in :ids")
    int markAsRead(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("ids") List<Long> ids);
}
