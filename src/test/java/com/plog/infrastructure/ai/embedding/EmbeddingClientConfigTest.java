package com.plog.infrastructure.ai.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class EmbeddingClientConfigTest {

    private final EmbeddingClientConfig config = new EmbeddingClientConfig();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void apiKey와_model이_있으면_Gemini를_사용한다() {
        EmbeddingClient client = config.embeddingClient(
                properties("gemini", "key-123", null, "gemini-embedding-001"), objectMapper);

        assertThat(client).isInstanceOf(GeminiEmbeddingClient.class);
        assertThat(client.isRealProvider()).isTrue();
    }

    /**
     * 키 없이도 앱이 떠야 한다 — 팀원 로컬과 CI 에는 키가 없다.
     * 여기서 예외가 나면 키를 안 넣은 모든 환경에서 기동이 깨진다.
     */
    @Test
    void apiKey가_없으면_Stub으로_폴백한다() {
        EmbeddingClient client = config.embeddingClient(
                properties("gemini", "", null, "gemini-embedding-001"), objectMapper);

        assertThat(client).isInstanceOf(StubEmbeddingClient.class);
        assertThat(client.isRealProvider()).isFalse();
    }

    @Test
    void gemini_provider인데_model이_없으면_Stub으로_폴백한다() {
        assertThat(config.embeddingClient(properties("gemini", "key-123", null, " "), objectMapper))
                .isInstanceOf(StubEmbeddingClient.class);
    }

    @Test
    void baseUrl과_model이_있으면_Ollama를_사용한다() {
        EmbeddingClient client = config.embeddingClient(
                properties("ollama", null, "http://localhost:11434", "bge-m3"), objectMapper);

        assertThat(client).isInstanceOf(OllamaEmbeddingClient.class);
        assertThat(client.isRealProvider()).isTrue();
    }

    @Test
    void ollama_provider인데_baseUrl이_없으면_Stub으로_폴백한다() {
        assertThat(config.embeddingClient(properties("ollama", null, "", "bge-m3"), objectMapper))
                .isInstanceOf(StubEmbeddingClient.class);
    }

    @Test
    void 알수없는_또는_기본_프로바이더는_Stub을_사용한다() {
        assertThat(config.embeddingClient(
                properties("stub", "key-123", "http://localhost:11434", "gemini-embedding-001"), objectMapper))
                .isInstanceOf(StubEmbeddingClient.class);
        assertThat(config.embeddingClient(
                properties(null, "key-123", "http://localhost:11434", "gemini-embedding-001"), objectMapper))
                .isInstanceOf(StubEmbeddingClient.class);
    }

    @Test
    void 프로퍼티_기본값이_적용된다() {
        EmbeddingProperties defaults = new EmbeddingProperties(null, null, null, null, null, null, 0);

        assertThat(defaults.provider()).isEqualTo("stub");
        assertThat(defaults.readTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(defaults.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(defaults.dimension()).isEqualTo(3072);
        assertThat(defaults.hasApiKey()).isFalse();
        assertThat(defaults.isGeminiUsable()).isFalse();
        assertThat(defaults.isOllamaUsable()).isFalse();
    }

    private EmbeddingProperties properties(String provider, String apiKey, String baseUrl, String model) {
        return new EmbeddingProperties(
                provider,
                apiKey,
                baseUrl,
                model,
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                3072
        );
    }
}