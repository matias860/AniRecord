package com.animelist.backend.repository;

import com.animelist.backend.entity.AppNotification;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {
    boolean existsByUserIdAndSourceKey(Long userId, String sourceKey);
    Optional<AppNotification> findByUserIdAndSourceKey(Long userId, String sourceKey);
    List<AppNotification> findByUserIdAndAnilistNotificationIdIn(Long userId, Collection<Integer> ids);
    List<AppNotification> findByUserIdAndSourceKeyIn(Long userId, Collection<String> sourceKeys);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "user")
    List<AppNotification> findByUserIdInAndSourceKeyIn(Collection<Long> userIds, Collection<String> sourceKeys);
    /**
     * Only select fields required by the UI. This keeps notification reads working
     * while an additive schema migration is still being applied in production.
     */
    @Query(value = """
        select id, notification_type, anilist_media_id, title,
            cover_image, message, episode, is_read, occurred_at
        from app_notification
        where user_id = :userId
        order by occurred_at desc
        """, nativeQuery = true)
    List<Object[]> findRowsByUserId(
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
