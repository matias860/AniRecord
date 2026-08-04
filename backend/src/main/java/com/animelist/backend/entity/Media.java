package com.animelist.backend.entity;

import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.ReleaseSeason;
import com.animelist.backend.enums.ReleaseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "media")
@Getter
@Setter
@NoArgsConstructor
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anilist_id", nullable = false, unique = true)
    private Integer anilistId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private MediaType mediaType;

    @Column(nullable = false)
    private String title;

    @Column(name = "cover_image", length = 1000)
    private String coverImage;

    @Column(name = "banner_image", length = 1000)
    private String bannerImage;

    @Column(columnDefinition = "TEXT")
    private String synopsis;

    @Column(name = "total_units")
    private Integer totalUnits;

    @Column(name = "release_start_date")
    private LocalDate releaseStartDate;

    @Column(name = "release_end_date")
    private LocalDate releaseEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "release_status")
    private ReleaseStatus releaseStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "release_season")
    private ReleaseSeason releaseSeason;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
