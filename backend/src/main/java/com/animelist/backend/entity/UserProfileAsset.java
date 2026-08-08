package com.animelist.backend.entity;

import com.animelist.backend.enums.ProfileAssetType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_profile_asset", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "asset_type"}))
@Getter @Setter @NoArgsConstructor
public class UserProfileAsset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 16)
    private ProfileAssetType assetType;
    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;
    @Column(nullable = false)
    private byte[] data;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @PrePersist @PreUpdate void touch() { updatedAt = LocalDateTime.now(); }
}
