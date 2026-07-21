package com.plog.domain.project.service;

import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
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

@Component
public class InviteTokenCipher {
    private static final int IV_LENGTH = 12;
    private static final int AUTH_TAG_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public InviteTokenCipher(@Value("${plog.invite.encryption-key-base64}") String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalArgumentException("plog.invite.encryption-key-base64 must be configured");
        }
        byte[] decoded = Base64.getDecoder().decode(encodedKey);
        if (decoded.length != 32) {
            throw new IllegalArgumentException("plog.invite.encryption-key-base64 must decode to 32 bytes");
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public String decrypt(String encryptedToken) {
        if (encryptedToken == null || encryptedToken.isBlank()) {
            throw new ApiException(ProjectErrorCode.INVITE_TOKEN_CONFIGURATION_ERROR);
        }
        try {
            byte[] packed = Base64.getDecoder().decode(encryptedToken);
            if (packed.length < IV_LENGTH + AUTH_TAG_LENGTH) {
                throw new IllegalArgumentException("encrypted invite token is too short");
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
            throw new ApiException(ProjectErrorCode.INVITE_TOKEN_CONFIGURATION_ERROR, exception);
        }
    }

    public String encrypt(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) {
            throw new ApiException(ProjectErrorCode.INVITE_TOKEN_CONFIGURATION_ERROR);
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(plainToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException exception) {
            throw new ApiException(ProjectErrorCode.INVITE_TOKEN_CONFIGURATION_ERROR, exception);
        }
    }
}
