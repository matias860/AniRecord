package com.animelist.backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "top_nine_submission", uniqueConstraints = {
        @UniqueConstraint(name = "uk_top_nine_user_period", columnNames = {"user_id", "period_start"})
})
@Getter
@Setter
@NoArgsConstructor
public class TopNineSubmission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "prompt_key", nullable = false, length = 64)
    private String promptKey;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<TopNineSelection> selections = new ArrayList<>();

    public void addSelection(Media media, int position) {
        TopNineSelection selection = new TopNineSelection();
        selection.setSubmission(this);
        selection.setMedia(media);
        selection.setPosition(position);
        selections.add(selection);
    }

    @PrePersist
    void onCreate() {
        if (publishedAt == null) publishedAt = LocalDateTime.now();
    }
}
