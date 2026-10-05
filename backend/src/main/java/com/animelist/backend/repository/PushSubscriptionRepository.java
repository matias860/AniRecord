package com.animelist.backend.repository;

import com.animelist.backend.entity.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    Optional<PushSubscription> findByEndpoint(String endpoint);
    Optional<PushSubscription> findByEndpointAndUserId(String endpoint, Long userId);
    List<PushSubscription> findByUserIdAndEnabledTrue(Long userId);
    @EntityGraph(attributePaths = "user")
    List<PushSubscription> findByEnabledTrue();
}
