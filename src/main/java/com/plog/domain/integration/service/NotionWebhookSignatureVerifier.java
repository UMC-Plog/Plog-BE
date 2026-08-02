package com.plog.domain.integration.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Notion의 X-Notion-Signature를 원문 body 기준으로 검증한다. */
@Component
class NotionWebhookSignatureVerifier {

    private static final String PREFIX = "sha256=";

    boolean verify(String verificationToken, String rawBody, String signature) {
        if (verificationToken == null || verificationToken.isBlank()
                || rawBody == null || signature == null || !signature.startsWith(PREFIX)) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    verificationToken.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = PREFIX + toHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    signature.getBytes(StandardCharsets.US_ASCII)
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Notion webhook signature verification failed", exception);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(value & 0x0f, 16));
        }
        return result.toString();
    }
}
