package com.animelist.backend.entity;

import com.animelist.backend.enums.PersonType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "person_favorite", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "anilist_id", "person_type"}))
@Getter @Setter @NoArgsConstructor
public class PersonFavorite {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private AppUser user;
    @Column(name = "anilist_id", nullable = false) private Integer anilistId;
    @Enumerated(EnumType.STRING) @Column(name = "person_type", nullable = false, length = 16) private PersonType personType;
    @Column(nullable = false, length = 255) private String name;
    @Column(name = "native_name", length = 255) private String nativeName;
    @Column(name = "image_url", length = 1000) private String imageUrl;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
}
