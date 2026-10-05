package com.animelist.backend.repository;

import com.animelist.backend.entity.PushDelivery;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushDeliveryRepository extends JpaRepository<PushDelivery, Long> {
    boolean existsBySubscriptionIdAndNotificationId(Long subscriptionId, Long notificationId);

    @EntityGraph(attributePaths = {"subscription", "notification"})
    @Query("select delivery from PushDelivery delivery where delivery.status in :statuses and delivery.attempts < 3 order by delivery.createdAt")
    List<PushDelivery> findReady(@Param("statuses") List<String> statuses, Pageable pageable);
}
