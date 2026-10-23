package com.animelist.backend.repository;

import com.animelist.backend.entity.PushDelivery;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushDeliveryRepository extends JpaRepository<PushDelivery, Long> {
    boolean existsBySubscriptionIdAndNotificationId(Long subscriptionId, Long notificationId);
    List<PushDelivery> findBySubscriptionIdInAndNotificationIdIn(
            List<Long> subscriptionIds, List<Long> notificationIds);

    @Query("""
        select delivery.id from PushDelivery delivery
        where delivery.attempts < 3 and (
            delivery.status = 'PENDING' or
            (delivery.status = 'FAILED' and (delivery.lastAttemptAt is null or delivery.lastAttemptAt < :retryBefore)) or
            (delivery.status = 'PROCESSING' and (delivery.lastAttemptAt is null or delivery.lastAttemptAt < :staleBefore))
        )
        order by delivery.createdAt
        """)
    List<Long> findClaimableIds(@Param("retryBefore") java.time.Instant retryBefore,
            @Param("staleBefore") java.time.Instant staleBefore, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PushDelivery delivery
        set delivery.status = 'PROCESSING', delivery.lastAttemptAt = :claimedAt,
            delivery.attempts = delivery.attempts + 1
        where delivery.id = :id and delivery.attempts < 3 and (
            delivery.status = 'PENDING' or
            (delivery.status = 'FAILED' and (delivery.lastAttemptAt is null or delivery.lastAttemptAt < :retryBefore)) or
            (delivery.status = 'PROCESSING' and (delivery.lastAttemptAt is null or delivery.lastAttemptAt < :staleBefore))
        )
        """)
    int claim(@Param("id") Long id, @Param("claimedAt") java.time.Instant claimedAt,
            @Param("retryBefore") java.time.Instant retryBefore,
            @Param("staleBefore") java.time.Instant staleBefore);

    @EntityGraph(attributePaths = {"subscription", "notification"})
    @Query("select delivery from PushDelivery delivery where delivery.id in :ids")
    List<PushDelivery> findClaimedWithDetails(@Param("ids") List<Long> ids);
}
