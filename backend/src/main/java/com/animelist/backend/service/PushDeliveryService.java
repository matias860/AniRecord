package com.animelist.backend.service;

import com.animelist.backend.entity.PushDelivery;
import com.animelist.backend.entity.PushSubscription;
import com.animelist.backend.exception.BusinessException;
import com.animelist.backend.repository.PushDeliveryRepository;
import com.animelist.backend.repository.PushSubscriptionRepository;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushDeliveryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PushDeliveryService.class);
    private final PushDeliveryRepository deliveries;
    private final PushDeliveryClaimService claims;
    private final PushSubscriptionRepository subscriptions;
    private final CurrentUserService currentUser;
    private final WebPushGateway gateway;
    private final ObjectMapper json;

    public PushDeliveryService(PushDeliveryRepository deliveries, PushDeliveryClaimService claims, PushSubscriptionRepository subscriptions,
            CurrentUserService currentUser, WebPushGateway gateway, ObjectMapper json) {
        this.deliveries = deliveries;
        this.claims = claims;
        this.subscriptions = subscriptions;
        this.currentUser = currentUser;
        this.gateway = gateway;
        this.json = json;
    }

    @Scheduled(initialDelayString = "${app.web-push.delivery-initial-delay:PT4M}", fixedDelayString = "${app.web-push.delivery-delay:PT1M}")
    public void deliverPending() {
        if (!gateway.available()) return;
        claims.claim(50).forEach(this::attempt);
    }

    public void sendTest(String endpoint) {
        var user = currentUser.require();
        PushSubscription subscription = subscriptions.findByEndpointAndUserId(endpoint, user.getId())
                .orElseThrow(() -> new BusinessException("Este dispositivo no tiene una suscripción activa."));
        try {
            int status = gateway.send(subscription, json.writeValueAsString(Map.of(
                    "title", "AniRecord está listo",
                    "body", "Las notificaciones de estrenos y episodios nuevos están activadas.",
                    "icon", "/pwa-notification-192x192.png",
                    "url", "/perfil",
                    "tag", "anirecord-test")));
            if (status < 200 || status >= 300) throw new BusinessException("El servicio push rechazó la prueba (HTTP " + status + ").");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.warn("No se pudo enviar una notificación push de prueba: {}", exception.getClass().getSimpleName());
            throw new BusinessException("No se pudo enviar la notificación de prueba.");
        }
    }

    private void attempt(PushDelivery delivery) {
        PushSubscription subscription = subscriptions.findById(delivery.getSubscription().getId())
                .filter(PushSubscription::isEnabled).orElse(null);
        if (subscription == null) return;
        try {
            var notification = delivery.getNotification();
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("title", notification.getEpisode() != null && notification.getEpisode() == 1 ? "Estreno disponible" : "Nuevo episodio disponible");
            message.put("body", notification.getMessage());
            message.put("url", notification.getAnilistMediaId() == null ? "/notificaciones" : "/media/" + notification.getAnilistMediaId());
            message.put("tag", notification.getSourceKey() == null ? "notification-" + notification.getId() : notification.getSourceKey());
            if (notification.getCoverImage() != null && !notification.getCoverImage().isBlank()) message.put("icon", notification.getCoverImage());
            String payload = json.writeValueAsString(message);
            int status = gateway.send(subscription, payload);
            if (status == 404 || status == 410) {
                disable(subscription);
                finish(delivery, "EXPIRED", "Suscripción vencida");
            } else if (status >= 200 && status < 300) {
                finish(delivery, "SENT", null);
            } else {
                fail(delivery, "HTTP " + status);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            fail(delivery, "Envío interrumpido");
        } catch (Exception exception) {
            LOGGER.warn("Falló una entrega push {}: {}", delivery.getId(), exception.getClass().getSimpleName());
            fail(delivery, exception.getClass().getSimpleName());
        }
    }

    @Transactional
    protected void finish(PushDelivery delivery, String status, String error) {
        delivery.setLastAttemptAt(Instant.now());
        delivery.setStatus(status);
        delivery.setLastError(error);
        if ("SENT".equals(status)) delivery.setDeliveredAt(Instant.now());
        deliveries.save(delivery);
    }

    private void fail(PushDelivery delivery, String error) { finish(delivery, "FAILED", error.substring(0, Math.min(error.length(), 300))); }

    @Transactional
    protected void disable(PushSubscription subscription) {
        subscription.setEnabled(false);
        subscriptions.save(subscription);
    }
}
