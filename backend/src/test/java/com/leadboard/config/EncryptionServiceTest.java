package com.leadboard.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    private EncryptionService createService(String key) {
        AppProperties props = new AppProperties();
        props.getEncryption().setTokenKey(key);
        return new EncryptionService(props);
    }

    @Test
    void encryptDecryptRoundtrip() {
        EncryptionService service = createService("my-secret-key-32chars-minimum!!");
        String original = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test-token-value";

        String encrypted = service.encrypt(original);
        assertNotNull(encrypted);
        assertNotEquals(original, encrypted);

        String decrypted = service.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void encryptProducesDifferentCiphertextEachTime() {
        EncryptionService service = createService("my-secret-key-32chars-minimum!!");
        String original = "same-plaintext-value";

        String encrypted1 = service.encrypt(original);
        String encrypted2 = service.encrypt(original);

        // Random IV means different ciphertext each time
        assertNotEquals(encrypted1, encrypted2);
        // Both decrypt to the same value
        assertEquals(original, service.decrypt(encrypted1));
        assertEquals(original, service.decrypt(encrypted2));
    }

    @Test
    void nullHandling() {
        EncryptionService service = createService("my-secret-key-32chars-minimum!!");

        assertNull(service.encrypt(null));
        assertNull(service.decrypt(null));
        assertNull(service.decryptSafe(null));
    }

    @Test
    void emptyStringHandling() {
        EncryptionService service = createService("my-secret-key-32chars-minimum!!");

        String encrypted = service.encrypt("");
        assertNotNull(encrypted);
        assertEquals("", service.decrypt(encrypted));
    }

    @Test
    void decryptSafeFallsBackToPlaintext() {
        EncryptionService service = createService("my-secret-key-32chars-minimum!!");

        // A plaintext JWT-like token should pass through decryptSafe
        String plaintext = "eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJhdGxhc3NpYW4ifQ.signature";
        String result = service.decryptSafe(plaintext);
        assertEquals(plaintext, result);
    }

    @Test
    void decryptSafeWorksWithEncryptedValues() {
        EncryptionService service = createService("my-secret-key-32chars-minimum!!");
        String original = "my-secret-token";
        String encrypted = service.encrypt(original);

        String result = service.decryptSafe(encrypted);
        assertEquals(original, result);
    }

    @Test
    void isLikelyEncryptedDetection() {
        EncryptionService service = createService("my-secret-key-32chars-minimum!!");

        // Short strings are not encrypted
        assertFalse(service.isLikelyEncrypted(null));
        assertFalse(service.isLikelyEncrypted(""));
        assertFalse(service.isLikelyEncrypted("short"));
        assertFalse(service.isLikelyEncrypted("a".repeat(63))); // just under 64

        // Hex strings >= 64 chars are likely encrypted (AES output is at least 64 hex chars)
        assertTrue(service.isLikelyEncrypted("a".repeat(64)));
        assertTrue(service.isLikelyEncrypted("a".repeat(65)));
        assertTrue(service.isLikelyEncrypted("0123456789abcdef".repeat(5)));

        // Non-hex characters mean not encrypted
        assertFalse(service.isLikelyEncrypted("g".repeat(65)));
        assertFalse(service.isLikelyEncrypted("eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJhdGxhc3NpYW4ifQ.signature-long-enough-to-exceed-64"));

        // Actual encrypted output should be detected
        String encrypted = service.encrypt("test-token");
        assertTrue(service.isLikelyEncrypted(encrypted));
    }

    @Test
    void noOpModeWhenKeyIsBlank() {
        EncryptionService service = createService("");

        assertFalse(service.isEnabled());

        String token = "my-plaintext-token";
        assertEquals(token, service.encrypt(token));
        assertEquals(token, service.decrypt(token));
        assertEquals(token, service.decryptSafe(token));
    }

    @Test
    void noOpModeWhenKeyIsNull() {
        EncryptionService service = createService(null);

        assertFalse(service.isEnabled());

        String token = "my-plaintext-token";
        assertEquals(token, service.encrypt(token));
    }
}
