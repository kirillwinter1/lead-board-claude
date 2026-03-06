package com.leadboard.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;

@Service
public class EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);

    private static volatile EncryptionService instance;

    private final TextEncryptor encryptor;
    private final boolean enabled;

    public EncryptionService(AppProperties appProperties) {
        String key = appProperties.getEncryption().getTokenKey();
        if (key == null || key.isBlank()) {
            log.info("TOKEN_ENCRYPTION_KEY not set — token encryption disabled (noOp mode)");
            this.encryptor = Encryptors.noOpText();
            this.enabled = false;
        } else {
            // Use a fixed salt derived from the key for deterministic encryptor creation.
            // Each encrypt() call still produces unique ciphertext via random IV.
            String hexSalt = "deadbeef";
            this.encryptor = Encryptors.text(key, hexSalt);
            this.enabled = true;
            log.info("Token encryption enabled");
        }
        instance = this;
    }

    static EncryptionService getInstance() {
        return instance;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        return encryptor.encrypt(plaintext);
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null) return null;
        return encryptor.decrypt(ciphertext);
    }

    /**
     * Tries to decrypt; if decryption fails (e.g. value is still plaintext), returns the original value.
     */
    public String decryptSafe(String value) {
        if (value == null) return null;
        if (!enabled) return value;
        if (!isLikelyEncrypted(value)) return value;
        try {
            return encryptor.decrypt(value);
        } catch (Exception e) {
            log.debug("Could not decrypt value, treating as plaintext");
            return value;
        }
    }

    /**
     * Heuristic: Spring Security Crypto's Encryptors.text() produces hex-encoded output
     * that is lowercase hex and significantly longer than typical plaintext tokens.
     */
    public boolean isLikelyEncrypted(String value) {
        if (value == null || value.length() < 64) return false;
        return value.matches("^[0-9a-f]+$");
    }
}
