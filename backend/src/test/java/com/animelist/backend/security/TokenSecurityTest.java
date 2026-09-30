package com.animelist.backend.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TokenSecurityTest {
    @Test void hashesSessionTokensWithoutPersistingTheBearerValue() {
        String raw = TokenSecurity.randomToken();
        assertEquals(64, raw.length());
        assertNotEquals(raw, TokenSecurity.digest(raw));
        assertEquals(TokenSecurity.digest(raw), TokenSecurity.digest(raw));
    }

    @Test void encryptsAndDecryptsAniListTokens() {
        var cipher = new SecretCipher("test-key-that-does-not-leave-the-test");
        String encrypted = cipher.encrypt("anilist-access-token");
        assertTrue(encrypted.startsWith("enc:v1:"));
        assertTrue(cipher.isEncrypted(encrypted));
        assertFalse(encrypted.contains("anilist-access-token"));
        assertEquals("anilist-access-token", cipher.decrypt(encrypted));
    }

    @Test void keepsLegacyPlaintextTokensReadableDuringMigration() {
        var cipher = new SecretCipher("test-key");
        assertEquals("legacy-token", cipher.decrypt("legacy-token"));
    }

    @Test void createsSelfContainedTamperProofOAuthStates() {
        var states = new OAuthStateToken("oauth-test-secret");
        String state = states.create();
        assertTrue(states.isValid(state));
        assertTrue(states.matches(state, state));
        assertFalse(states.matches(state, states.create()));
        assertFalse(states.matches(state, null));
        assertFalse(states.isValid(state + "alterado"));
        assertFalse(new OAuthStateToken("different-secret").isValid(state));
    }
}
