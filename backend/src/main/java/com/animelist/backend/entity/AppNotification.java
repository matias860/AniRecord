package com.animelist.backend.entity;

import com.animelist.backend.enums.NotificationType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_notification")
@Getter @Setter @NoArgsConstructor
public class AppNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "anilist_notification_id", nullable = false)
    private Integer anilistNotificationId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name="user_id", nullable=false)
    private AppUser user;
    @Enumerated(EnumType.STRING) @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;
    @Column(name = "anilist_media_id")
    private Integer anilistMediaId;
    @Column(length = 255)
    private String title;
    @Column(name = "cover_image", length = 1000)
    private String coverImage;
    @Column(length = 1000)
    private String message;
    private Integer episode;
    @Column(name = "is_read", nullable = false)
    private Boolean read = false;
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
