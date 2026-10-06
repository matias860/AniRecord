package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.animelist.backend.entity.PushDelivery;
import com.animelist.backend.repository.PushDeliveryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class PushDeliveryClaimServiceTest {
    @Test
    void processesOnlyRowsWonByTheAtomicClaim() {
        PushDeliveryRepository deliveries = mock(PushDeliveryRepository.class);
        PushDelivery delivery = new PushDelivery();
        delivery.setId(10L);
        when(deliveries.findClaimableIds(any(), any(Pageable.class))).thenReturn(List.of(10L, 11L));
        when(deliveries.claim(eq(10L), any(), any())).thenReturn(1);
        when(deliveries.claim(eq(11L), any(), any())).thenReturn(0);
        when(deliveries.findClaimedWithDetails(List.of(10L))).thenReturn(List.of(delivery));

        var claimed = new PushDeliveryClaimService(deliveries).claim(50);

        assertEquals(List.of(delivery), claimed);
        verify(deliveries).findClaimedWithDetails(List.of(10L));
    }
}
