package com.animelist.backend.entity;

import com.animelist.backend.enums.ActivityType;
import com.animelist.backend.enums.TrackingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "activity_history")
@Getter
@Setter
@NoArgsConstructor
public class ActivityHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private Media media;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false)
    private ActivityType activityType;

    @Column(name = "previous_progress")
    private Integer previousProgress;

    @Column(name = "new_progress")
    private Integer newProgress;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private TrackingStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status")
    private TrackingStatus newStatus;

    @Column(name = "previous_score", precision = 3, scale = 1)
    private BigDecimal previousScore;

    @Column(name = "new_score", precision = 3, scale = 1)
    private BigDecimal newScore;

    @Column(name = "previous_rewatch_count")
    private Integer previousRewatchCount;

    @Column(name = "new_rewatch_count")
    private Integer newRewatchCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
