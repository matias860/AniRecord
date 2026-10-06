package com.animelist.backend.service;

import com.animelist.backend.entity.PushSubscription;
import java.nio.charset.StandardCharsets;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

@Component
public class WebPushGateway {
    private final PushService pushService;

    public WebPushGateway(
            @Value("${app.web-push.public-key:}") String publicKey,
            @Value("${app.web-push.private-key:}") String privateKey,
            @Value("${app.web-push.subject:mailto:admin@anirecord.com.ar}") String subject) {
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) Security.addProvider(new BouncyCastleProvider());
            this.pushService = publicKey == null || publicKey.isBlank() || privateKey == null || privateKey.isBlank()
                    ? null : new PushService(publicKey.trim(), privateKey.trim(), subject);
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo inicializar Web Push", exception);
        }
    }

    public boolean available() { return pushService != null; }

    public int send(PushSubscription subscription, String payload) throws Exception {
        if (pushService == null) throw new IllegalStateException("Web Push no está configurado");
        Notification notification = new Notification(
                subscription.getEndpoint(), subscription.getP256dhKey(), subscription.getAuthKey(),
                payload.getBytes(StandardCharsets.UTF_8), 300);
        // aes128gcm is the current Web Push content encoding. The library's
        // one-argument overload still falls back to the legacy aesgcm format.
        HttpResponse response = pushService.send(notification, Encoding.AES128GCM);
        return response.getStatusLine().getStatusCode();
    }
}
