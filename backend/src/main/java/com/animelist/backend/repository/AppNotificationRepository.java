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
     * Proyección explícita: evita cargar columnas nuevas al leer notificaciones
     * creadas antes de una migración de esquema pendiente.
     */
    @Query("""
        select new com.animelist.backend.dto.notification.NotificationResponse(
            notification.id, notification.notificationType, notification.anilistMediaId,
            notification.title, notification.coverImage, notification.message,
            notification.episode, notification.read, notification.occurredAt
        )
        from AppNotification notification
        where notification.user.id = :userId
        order by notification.occurredAt desc
        """)
    List<NotificationResponse> findResponsesByUserId(
        @org.springframework.data.repository.query.Param("userId") Long userId,
        Pageable pageable);
    long countByUserIdAndReadFalse(Long userId);
    long countByUserId(Long userId);
    @Modifying
    @Query("update AppNotification notification set notification.read = true where notification.user.id = :userId and notification.read = false")
    int markAllAsRead(@org.springframework.data.repository.query.Param("userId") Long userId);
}
