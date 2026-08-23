package com.animelist.backend.entity;

import com.animelist.backend.enums.MediaType;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "media_relation_snapshot", uniqueConstraints = @UniqueConstraint(
        name = "uk_media_relation_snapshot",
        columnNames = {"source_media_id", "related_anilist_id", "relation_type"}
))
@Getter
@Setter
@NoArgsConstructor
public class MediaRelationSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_media_id", nullable = false)
    private Media sourceMedia;

    @Column(name = "related_anilist_id", nullable = false)
    private Integer relatedAnilistId;

    @Column(name = "relation_type", nullable = false, length = 40)
    private String relationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "related_media_type", nullable = false, length = 16)
    private MediaType relatedMediaType;

    @Column(name = "related_format", length = 32)
    private String relatedFormat;

    @Column(name = "related_title", nullable = false)
    private String relatedTitle;

    @Column(name = "related_cover_image", length = 1000)
    private String relatedCoverImage;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private LocalDateTime firstSeenAt;

    @PrePersist
    void onCreate() {
        if (firstSeenAt == null) firstSeenAt = LocalDateTime.now();
    }
}
