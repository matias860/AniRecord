package com.animelist.backend.repository;

import com.animelist.backend.entity.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    Optional<PushSubscription> findByEndpoint(String endpoint);
    Optional<PushSubscription> findByEndpointAndUserId(String endpoint, Long userId);
    List<PushSubscription> findByUserIdAndEnabledTrue(Long userId);
    @Modifying
    @Query("delete from PushSubscription subscription where subscription.endpoint = :endpoint and subscription.user.id = :userId")
    int deleteByEndpointAndUserId(@Param("endpoint") String endpoint, @Param("userId") Long userId);
    @EntityGraph(attributePaths = "user")
    List<PushSubscription> findByEnabledTrue();
}
