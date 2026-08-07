package com.animelist.backend.repository;

import com.animelist.backend.entity.AppSession;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSessionRepository extends JpaRepository<AppSession, String> {
    @EntityGraph(attributePaths = "user") Optional<AppSession> findByTokenAndExpiresAtAfter(String token, LocalDateTime now);
    void deleteByExpiresAtBefore(LocalDateTime now);
}
