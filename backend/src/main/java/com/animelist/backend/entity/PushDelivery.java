package com.animelist.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "push_delivery")
@Getter @Setter @NoArgsConstructor
public class PushDelivery {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private PushSubscription subscription;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private AppNotification notification;
    @Column(nullable = false, length = 16)
    private String status = "PENDING";
    @Column(nullable = false)
    private int attempts;
    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;
    @Column(name = "delivered_at")
    private Instant deliveredAt;
    @Column(name = "last_error", length = 300)
    private String lastError;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @PrePersist void onCreate() { createdAt = Instant.now(); }
}
