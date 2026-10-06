package com.animelist.backend.service;

import com.animelist.backend.entity.PushDelivery;
import com.animelist.backend.repository.PushDeliveryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushDeliveryClaimService {
    private static final Duration ABANDONED_AFTER = Duration.ofMinutes(10);
    private final PushDeliveryRepository deliveries;

    public PushDeliveryClaimService(PushDeliveryRepository deliveries) {
        this.deliveries = deliveries;
    }

    @Transactional
    public List<PushDelivery> claim(int limit) {
        Instant claimedAt = Instant.now();
        Instant staleBefore = claimedAt.minus(ABANDONED_AFTER);
        List<Long> claimedIds = deliveries.findClaimableIds(staleBefore, PageRequest.of(0, limit)).stream()
                .filter(id -> deliveries.claim(id, claimedAt, staleBefore) == 1)
                .toList();
        return claimedIds.isEmpty() ? List.of() : deliveries.findClaimedWithDetails(claimedIds);
    }
}
