package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class NotionWebhookSignatureVerifierTest {

    private final NotionWebhookSignatureVerifier verifier = new NotionWebhookSignatureVerifier();

    @Test
    void verifiesSignatureAgainstExactRawBody() throws Exception {
        String token = "verification-token";
        String rawBody = "{\"type\":\"page.content_updated\",\"title\":\"회의록\"}";
        String signature = signature(token, rawBody);

        assertThat(verifier.verify(token, rawBody, signature)).isTrue();
        assertThat(verifier.verify(token, rawBody + " ", signature)).isFalse();
        assertThat(verifier.verify("wrong-token", rawBody, signature)).isFalse();
    }

    @Test
    void rejectsMissingConfigurationOrSignature() {
        assertThat(verifier.verify(null, "{}", "sha256=value")).isFalse();
        assertThat(verifier.verify("token", "{}", null)).isFalse();
        assertThat(verifier.verify("token", "{}", "invalid")).isFalse();
    }

    private String signature(String token, String rawBody) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + java.util.HexFormat.of().formatHex(
                mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
    }
}
