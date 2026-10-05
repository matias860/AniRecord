package com.animelist.backend.repository;

import com.animelist.backend.entity.OAuthLoginTicket;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface OAuthLoginTicketRepository extends JpaRepository<OAuthLoginTicket, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "user")
    Optional<OAuthLoginTicket> findByTokenAndExpiresAtAfter(String token, LocalDateTime now);
    void deleteByExpiresAtBefore(LocalDateTime now);
}
