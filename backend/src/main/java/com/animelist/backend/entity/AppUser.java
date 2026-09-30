package com.animelist.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import com.animelist.backend.enums.AccountType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "anilist_user_id", unique = true)
    private Integer anilistUserId;
    @Column(name = "anilist_username", unique = true, length = 100)
    private String anilistUsername;
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 16)
    private AccountType accountType = AccountType.ANILIST;
    @Column(name = "local_username", length = 30)
    private String localUsername;
    @Column(name = "password_hash", length = 100)
    private String passwordHash;
    @Column(name = "display_name", nullable = false, length = 40)
    private String displayName = "Mi perfil";
    @Column(nullable = false, length = 160)
    private String bio = "Coleccionando historias, un episodio a la vez.";
    @Column(name = "avatar_url", length = 1000)
    private String avatarUrl;
    @Column(name = "banner_url", length = 1000)
    private String bannerUrl;
    @Column(name = "background_color", nullable = false, length = 7)
    private String backgroundColor = "#0a0c14";
    @Column(name = "surface_color", nullable = false, length = 7)
    private String surfaceColor = "#171b29";
    @Column(name = "accent_color", nullable = false, length = 7)
    private String accentColor = "#a88bff";
    @Column(name = "dots_enabled", nullable = false)
    private Boolean dotsEnabled = true;
    @Column(name = "last_anilist_sync_at")
    private LocalDateTime lastAniListSyncAt;
    @Column(name = "notifications_initialized_at")
    private LocalDateTime notificationsInitializedAt;
    @Column(name = "notification_backfill_page")
    private Integer notificationBackfillPage;
    @Column(name = "notification_catchup_page")
    private Integer notificationCatchupPage;
    @Column(name = "last_sync_prompt_commit", length = 64)
    private String lastSyncPromptCommit;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @PrePersist void onCreate() { var now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
