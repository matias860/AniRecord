package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

class WebPushGatewayTest {
    @Test void acceptsAValidVapidKeyPair() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", "BC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var pair = generator.generateKeyPair();
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String publicKey = encoder.encodeToString(Utils.encode((ECPublicKey) pair.getPublic()));
        String privateKey = encoder.encodeToString(Utils.encode((ECPrivateKey) pair.getPrivate()));
        assertTrue(new WebPushGateway(publicKey, privateKey, "mailto:test@anirecord.com.ar").available());
    }
}
