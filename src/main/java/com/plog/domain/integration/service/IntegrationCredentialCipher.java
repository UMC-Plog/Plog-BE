package com.plog.domain.integration.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** OAuth access/refresh token을 DB에 저장하기 전에 AES-GCM으로 암호화한다. */
@Component
public class IntegrationCredentialCipher {
    private static final int AES_256_KEY_LENGTH = 32;
    private static final int IV_LENGTH = 12;
    private static final int AUTH_TAG_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public IntegrationCredentialCipher(
            @Value("${plog.integration.encryption-key-base64:}") String encodedKey
    ) {
        this.key = decodeKey(encodedKey);
    }

    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        requireConfigured();
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("integration credential encryption failed", exception);
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null) {
            return null;
        }
        requireConfigured();
        try {
            byte[] packed = Base64.getDecoder().decode(encryptedText);
            if (packed.length < IV_LENGTH + AUTH_TAG_LENGTH) {
                throw new IllegalArgumentException("encrypted integration credential is too short");
            }
            ByteBuffer buffer = ByteBuffer.wrap(packed);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("integration credential decryption failed", exception);
        }
    }

    private SecretKeySpec decodeKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            return null;
        }
        byte[] decoded = Base64.getDecoder().decode(encodedKey);
        if (decoded.length != AES_256_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "plog.integration.encryption-key-base64 must decode to 32 bytes");
        }
        return new SecretKeySpec(decoded, "AES");
    }

    private void requireConfigured() {
        if (key == null) {
            throw new IllegalStateException("plog.integration.encryption-key-base64 must be configured");
        }
    }
}
