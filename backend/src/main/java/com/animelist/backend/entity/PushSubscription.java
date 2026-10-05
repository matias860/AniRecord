package com.animelist.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "push_subscription")
@Getter @Setter @NoArgsConstructor
public class PushSubscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String endpoint;
    @Column(name = "p256dh_key", nullable = false, length = 180)
    private String p256dhKey;
    @Column(name = "auth_key", nullable = false, length = 80)
    private String authKey;
    @Column(name = "premieres_enabled", nullable = false)
    private boolean premieresEnabled = true;
    @Column(name = "episodes_enabled", nullable = false)
    private boolean episodesEnabled = true;
    @Column(nullable = false)
    private boolean enabled = true;
    @Column(name = "user_agent", length = 255)
    private String userAgent;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
}
