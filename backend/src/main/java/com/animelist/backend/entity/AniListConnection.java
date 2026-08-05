package com.animelist.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "anilist_connection")
@Getter @Setter @NoArgsConstructor
public class AniListConnection {
    @Id private Byte id = 1;
    @Column(name="access_token", nullable=false, columnDefinition="TEXT") private String accessToken;
    @Column(name="token_expires_at") private LocalDateTime tokenExpiresAt;
    @Column(nullable=false, length=50) private String username;
    @Column(name="connected_at", nullable=false) private LocalDateTime connectedAt;
    @PrePersist void onCreate() { if (connectedAt == null) connectedAt = LocalDateTime.now(); }
}
