package com.animelist.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class OAuthStateToken {
    private final SecretKeySpec key;

    public OAuthStateToken(String secret) {
        if (secret == null || secret.isBlank()) throw new IllegalArgumentException("Falta la clave OAuth");
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String create() {
        long expiry = Instant.now().plusSeconds(600).getEpochSecond();
        String payload = expiry + ":" + TokenSecurity.randomToken();
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + sign(encoded);
    }

    public boolean isValid(String state) {
        try {
            String[] parts = state.split("\\.", 2);
            if (parts.length != 2 || !MessageDigest.isEqual(
                    parts[1].getBytes(StandardCharsets.UTF_8), sign(parts[0]).getBytes(StandardCharsets.UTF_8))) return false;
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            long expiry = Long.parseLong(payload.substring(0, payload.indexOf(':')));
            return expiry >= Instant.now().getEpochSecond();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo firmar el estado OAuth", exception);
        }
    }
}
