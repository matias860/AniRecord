package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.animelist.backend.entity.AppNotification;
import com.animelist.backend.entity.PushDelivery;
import com.animelist.backend.entity.PushSubscription;
import com.animelist.backend.enums.NotificationType;
import com.animelist.backend.repository.PushDeliveryRepository;
import com.animelist.backend.repository.PushSubscriptionRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class PushDeliveryServiceTest {
    @Test
    void finishesClaimedDeliveryWhenSubscriptionWasDisabled() {
        PushDeliveryRepository deliveries = mock(PushDeliveryRepository.class);
        PushDeliveryClaimService claims = mock(PushDeliveryClaimService.class);
        PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
        WebPushGateway gateway = mock(WebPushGateway.class);
        PushDeliveryService service = new PushDeliveryService(deliveries, claims, subscriptions,
                mock(CurrentUserService.class), gateway, mock(ObjectMapper.class));
        PushSubscription subscription = new PushSubscription();
        subscription.setId(11L);
        PushDelivery delivery = new PushDelivery();
        delivery.setId(23L);
        delivery.setSubscription(subscription);
        when(claims.claim(50)).thenReturn(List.of(delivery));
        // The scheduler only runs when push is configured; the subscription can be
        // disabled between claiming the row and attempting the delivery.
        when(gateway.available()).thenReturn(true);
        when(subscriptions.findById(11L)).thenReturn(Optional.empty());

        service.recoverPending();

        assertEquals("SKIPPED", delivery.getStatus());
        assertEquals("Suscripción inactiva", delivery.getLastError());
        verify(deliveries).save(delivery);
    }

    @Test
    void sendsCommunityReminderBackToTheHomePage() throws Exception {
        PushDeliveryRepository deliveries = mock(PushDeliveryRepository.class);
        PushDeliveryClaimService claims = mock(PushDeliveryClaimService.class);
        PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        WebPushGateway gateway = mock(WebPushGateway.class);
        ObjectMapper json = mock(ObjectMapper.class);
        PushDeliveryService service = new PushDeliveryService(
                deliveries, claims, subscriptions, currentUser, gateway, json);

        PushSubscription subscription = new PushSubscription();
        subscription.setId(11L);
        subscription.setEnabled(true);
        AppNotification notification = new AppNotification();
        notification.setId(19L);
        notification.setNotificationType(NotificationType.TOP_NINE_AVAILABLE);
        notification.setMessage("Ya podés armar el nuevo 3×3.");
        notification.setSourceKey("TOP_NINE_AVAILABLE:2026-10-16");
        PushDelivery delivery = new PushDelivery();
        delivery.setId(23L);
        delivery.setSubscription(subscription);
        delivery.setNotification(notification);

        when(gateway.available()).thenReturn(true);
        when(claims.claim(50)).thenReturn(List.of(delivery));
        when(subscriptions.findById(11L)).thenReturn(Optional.of(subscription));
        when(json.writeValueAsString(any())).thenReturn("{}");
        when(gateway.send(subscription, "{}")).thenReturn(201);

        service.recoverPending();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(json).writeValueAsString(payload.capture());
        assertEquals("Nuevo 3×3 disponible", payload.getValue().get("title"));
        assertEquals("/", payload.getValue().get("url"));
        verify(deliveries).save(delivery);
        assertEquals("SENT", delivery.getStatus());
    }
}
