package com.plog.domain.integration.service;

import com.plog.domain.integration.config.GithubIntegrationProperties;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.exception.ApiException;
import io.jsonwebtoken.Jwts;
import java.io.ByteArrayOutputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.springframework.stereotype.Component;

@Component
public class GithubAppJwtFactory {
    private final GithubIntegrationProperties properties;

    public GithubAppJwtFactory(GithubIntegrationProperties properties) {
        this.properties = properties;
    }

    public String create() {
        try {
            Instant now = Instant.now();
            return Jwts.builder()
                    .issuer(require(properties.appId()))
                    .issuedAt(Date.from(now.minusSeconds(30)))
                    .expiration(Date.from(now.plusSeconds(540)))
                    .signWith(readPrivateKey())
                    .compact();
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(IntegrationErrorCode.PROVIDER_CONFIGURATION_ERROR, exception);
        }
    }

    private PrivateKey readPrivateKey() throws Exception {
        String pem = new String(Base64.getDecoder().decode(require(properties.privateKeyBase64())));
        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        if (pem.contains("BEGIN RSA PRIVATE KEY")) {
            keyBytes = wrapPkcs1InPkcs8(keyBytes);
        }
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private byte[] wrapPkcs1InPkcs8(byte[] pkcs1) {
        byte[] algorithmIdentifier = new byte[] {
                0x30, 0x0d,
                0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01,
                0x05, 0x00
        };
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        content.write(0x02);
        content.write(0x01);
        content.write(0x00);
        content.writeBytes(algorithmIdentifier);
        content.write(0x04);
        writeLength(content, pkcs1.length);
        content.writeBytes(pkcs1);

        byte[] body = content.toByteArray();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(0x30);
        writeLength(result, body.length);
        result.writeBytes(body);
        return result.toByteArray();
    }

    private void writeLength(ByteArrayOutputStream output, int length) {
        if (length < 128) {
            output.write(length);
            return;
        }
        int size = 0;
        int value = length;
        while (value > 0) {
            size++;
            value >>>= 8;
        }
        output.write(0x80 | size);
        for (int shift = (size - 1) * 8; shift >= 0; shift -= 8) {
            output.write((length >>> shift) & 0xff);
        }
    }

    private String require(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(IntegrationErrorCode.PROVIDER_CONFIGURATION_ERROR);
        }
        return value;
    }
}
