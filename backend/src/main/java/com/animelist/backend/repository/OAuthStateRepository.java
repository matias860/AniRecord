package com.animelist.backend.repository;

import com.animelist.backend.entity.OAuthState;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthStateRepository extends JpaRepository<OAuthState, String> {
    void deleteByExpiresAtBefore(LocalDateTime now);
}
