package com.animelist.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "media_relation_watch")
@Getter
@Setter
@NoArgsConstructor
public class MediaRelationWatch {
    @Id
    @Column(name = "media_id")
    private Long mediaId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "media_id")
    private Media media;

    @Column(name = "initialized_at", nullable = false, updatable = false)
    private LocalDateTime initializedAt;

    @Column(name = "last_checked_at", nullable = false)
    private LocalDateTime lastCheckedAt;

    public void setMedia(Media media) {
        this.media = media;
        this.mediaId = media == null ? null : media.getId();
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (initializedAt == null) initializedAt = now;
        if (lastCheckedAt == null) lastCheckedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        lastCheckedAt = LocalDateTime.now();
    }
}
