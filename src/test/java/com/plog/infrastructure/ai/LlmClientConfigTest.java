package com.plog.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LlmClientConfigTest {

    private final LlmClientConfig config = new LlmClientConfig();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void usesGeminiWhenProviderAndKeyArePresent() {
        LlmClient client = config.llmClient(properties("gemini", "key-123", "gemini-2.5-flash"), objectMapper);

        assertThat(client).isInstanceOf(GeminiLlmClient.class);
        assertThat(client.isRealProvider()).isTrue();
    }

    /**
     * 키 없이도 앱이 떠야 한다 — 팀원 로컬과 CI 에는 키가 없다.
     * 여기서 예외가 나면 키를 안 넣은 모든 환경에서 기동이 깨진다.
     */
    @Test
    void fallsBackToStubWhenApiKeyIsMissing() {
        LlmClient client = config.llmClient(properties("gemini", "", "gemini-2.5-flash"), objectMapper);

        assertThat(client).isInstanceOf(StubLlmClient.class);
        assertThat(client.isRealProvider()).isFalse();
    }

    @Test
    void fallsBackToStubWhenModelIsMissing() {
        assertThat(config.llmClient(properties("gemini", "key-123", " "), objectMapper))
                .isInstanceOf(StubLlmClient.class);
    }

    @Test
    void usesStubForUnknownOrDefaultProvider() {
        assertThat(config.llmClient(properties("stub", "key-123", "gemini-2.5-flash"), objectMapper))
                .isInstanceOf(StubLlmClient.class);
        assertThat(config.llmClient(properties(null, "key-123", "gemini-2.5-flash"), objectMapper))
                .isInstanceOf(StubLlmClient.class);
    }

    @Test
    void stubReturnsParseableCannedJson() {
        LlmClient client = config.llmClient(properties("stub", null, null), objectMapper);

        LlmResponse response = client.generate(new LlmRequest(
                "system", "user", null, 2048, 0.3));

        assertThat(response.text()).contains("headline");
        assertThat(response.model()).isEqualTo("stub");
    }

    @Test
    void propertiesApplyDefaultsAndRejectInvalidTemperature() {
        LlmProperties defaults = new LlmProperties(null, null, null, null, null, null, 0, 0.3);

        assertThat(defaults.provider()).isEqualTo("stub");
        assertThat(defaults.readTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(defaults.maxOutputTokens()).isEqualTo(2048);
        assertThat(defaults.hasApiKey()).isFalse();

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        new LlmProperties("stub", null, null, null, null, null, 100, 1.5))
                .isInstanceOf(IllegalStateException.class);
    }

    private LlmProperties properties(String provider, String apiKey, String model) {
        return new LlmProperties(
                provider,
                apiKey,
                model,
                "https://generativelanguage.googleapis.com",
                Duration.ofSeconds(5),
                Duration.ofSeconds(60),
                2048,
                0.3
        );
    }
}
