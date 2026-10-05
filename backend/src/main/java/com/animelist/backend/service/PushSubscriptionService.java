package com.animelist.backend.service;

import com.animelist.backend.dto.push.*;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.entity.PushSubscription;
import com.animelist.backend.exception.BusinessException;
import com.animelist.backend.repository.PushSubscriptionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Base64;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushSubscriptionService {
    private static final Set<String> PUSH_HOSTS = Set.of(
            "fcm.googleapis.com", "updates.push.services.mozilla.com", "push.services.mozilla.com", "web.push.apple.com");
    private final PushSubscriptionRepository subscriptions;
    private final CurrentUserService currentUser;
    private final HttpServletRequest request;
    private final String publicKey;

    public PushSubscriptionService(PushSubscriptionRepository subscriptions, CurrentUserService currentUser,
            HttpServletRequest request, @Value("${app.web-push.public-key:}") String publicKey) {
        this.subscriptions = subscriptions;
        this.currentUser = currentUser;
        this.request = request;
        this.publicKey = publicKey == null ? "" : publicKey.trim();
    }

    public PushConfigResponse config() {
        currentUser.require();
        return new PushConfigResponse(!publicKey.isBlank(), publicKey);
    }

    @Transactional
    public PushSubscriptionResponse save(PushSubscriptionRequest input) {
        if (publicKey.isBlank()) throw new BusinessException("Las notificaciones push todavía no están disponibles.");
        validate(input);
        AppUser user = currentUser.require();
        PushSubscription subscription = subscriptions.findByEndpoint(input.endpoint()).orElseGet(PushSubscription::new);
        if (subscription.getId() != null && !subscription.getUser().getId().equals(user.getId())
                && (!subscription.getP256dhKey().equals(input.keys().p256dh()) || !subscription.getAuthKey().equals(input.keys().auth()))) {
            throw new BusinessException("La suscripción no coincide con este dispositivo.");
        }
        subscription.setUser(user);
        subscription.setEndpoint(input.endpoint());
        subscription.setP256dhKey(input.keys().p256dh());
        subscription.setAuthKey(input.keys().auth());
        subscription.setPremieresEnabled(input.premieresEnabled());
        subscription.setEpisodesEnabled(input.episodesEnabled());
        subscription.setEnabled(true);
        String userAgent = request.getHeader("User-Agent");
        subscription.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 255)));
        subscriptions.save(subscription);
        return response(subscription);
    }

    @Transactional(readOnly = true)
    public PushSubscriptionResponse status(PushEndpointRequest input) {
        AppUser user = currentUser.require();
        return subscriptions.findByEndpointAndUserId(input.endpoint(), user.getId())
                .map(this::response).orElse(new PushSubscriptionResponse(false, true, true));
    }

    @Transactional
    public PushSubscriptionResponse preferences(PushSubscriptionRequest input) {
        return save(input);
    }

    @Transactional
    public void delete(PushEndpointRequest input) {
        AppUser user = currentUser.require();
        subscriptions.findByEndpointAndUserId(input.endpoint(), user.getId()).ifPresent(subscriptions::delete);
    }

    private PushSubscriptionResponse response(PushSubscription value) {
        return new PushSubscriptionResponse(value.isEnabled(), value.isPremieresEnabled(), value.isEpisodesEnabled());
    }

    private void validate(PushSubscriptionRequest input) {
        try {
            URI endpoint = URI.create(input.endpoint());
            String host = endpoint.getHost() == null ? "" : endpoint.getHost().toLowerCase(java.util.Locale.ROOT);
            boolean trustedHost = PUSH_HOSTS.contains(host) || host.endsWith(".notify.windows.com") || host.endsWith(".push.apple.com");
            if (!"https".equalsIgnoreCase(endpoint.getScheme()) || !trustedHost || endpoint.getUserInfo() != null || endpoint.getPort() != -1) throw new IllegalArgumentException();
            Base64.getUrlDecoder().decode(pad(input.keys().p256dh()));
            Base64.getUrlDecoder().decode(pad(input.keys().auth()));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("La suscripción push no es válida.");
        }
    }

    private String pad(String value) {
        int remainder = value.length() % 4;
        return remainder == 0 ? value : value + "=".repeat(4 - remainder);
    }
}
