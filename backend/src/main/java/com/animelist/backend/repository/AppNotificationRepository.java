package com.animelist.backend.repository;

import com.animelist.backend.dto.notification.NotificationRow;
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
    @Query(value = """
        select id as "id", notification_type as "type",
            anilist_media_id as "anilistMediaId", title as "title",
            cover_image as "coverImage", message as "message", episode as "episode",
            is_read as "read", occurred_at as "occurredAt"
        from app_notification
        where user_id = :userId
        order by occurred_at desc
        """, nativeQuery = true)
    List<NotificationRow> findRowsByUserId(
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
