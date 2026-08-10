package com.plog.infrastructure.ai.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class EmbeddingClientConfigTest {

    private final EmbeddingClientConfig config = new EmbeddingClientConfig();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void gemini_apiKey와_model이_있으면_Gemini를_사용한다() {
        EmbeddingClient client = config.embeddingClient(
                properties(gemini("key-123", "gemini-embedding-001"), ollama(null, null)), objectMapper);

        assertThat(client).isInstanceOf(GeminiEmbeddingClient.class);
        assertThat(client.isRealProvider()).isTrue();
    }

    /**
     * 키 없이도 앱이 떠야 한다 — 팀원 로컬과 CI 에는 키가 없다.
     * 여기서 예외가 나면 키를 안 넣은 모든 환경에서 기동이 깨진다.
     */
    @Test
    void gemini_설정도_ollama_설정도_없으면_Stub으로_폴백한다() {
        EmbeddingClient client = config.embeddingClient(
                properties(gemini(null, "gemini-embedding-001"), ollama(null, null)), objectMapper);

        assertThat(client).isInstanceOf(StubEmbeddingClient.class);
        assertThat(client.isRealProvider()).isFalse();
    }

    @Test
    void Gemini가_안_되고_Ollama_설정이_있으면_Ollama를_선택한다() {
        // 핵심 회귀 테스트: provider 문자열로 서로를 배타적으로 gating하던 예전 버그가
        // 재발하지 않는지 확인한다 — Gemini 키가 없어도 Ollama 설정이 채워져 있으면
        // 그쪽으로 정상적으로 넘어가야 한다.
        EmbeddingClient client = config.embeddingClient(
                properties(gemini(null, "gemini-embedding-001"), ollama("http://localhost:11434", "bge-m3")),
                objectMapper);

        assertThat(client).isInstanceOf(OllamaEmbeddingClient.class);
        assertThat(client.isRealProvider()).isTrue();
    }

    @Test
    void Gemini와_Ollama_둘_다_설정돼_있으면_Gemini를_우선한다() {
        EmbeddingClient client = config.embeddingClient(
                properties(gemini("key-123", "gemini-embedding-001"), ollama("http://localhost:11434", "bge-m3")),
                objectMapper);

        assertThat(client).isInstanceOf(GeminiEmbeddingClient.class);
    }

    @Test
    void gemini_model이_없으면_Stub으로_폴백한다() {
        assertThat(config.embeddingClient(properties(gemini("key-123", " "), ollama(null, null)), objectMapper))
                .isInstanceOf(StubEmbeddingClient.class);
    }

    @Test
    void ollama_model이_없으면_Stub으로_폴백한다() {
        assertThat(config.embeddingClient(
                properties(gemini(null, null), ollama("http://localhost:11434", " ")), objectMapper))
                .isInstanceOf(StubEmbeddingClient.class);
    }

    @Test
    void 프로퍼티_기본값이_적용된다() {
        EmbeddingProperties defaults = new EmbeddingProperties(null, null, null, null, 0);

        assertThat(defaults.gemini().baseUrl()).isEqualTo("https://generativelanguage.googleapis.com");
        assertThat(defaults.readTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(defaults.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(defaults.dimension()).isEqualTo(3072);
        assertThat(defaults.isGeminiUsable()).isFalse();
        assertThat(defaults.isOllamaUsable()).isFalse();
    }

    private EmbeddingProperties.GeminiConfig gemini(String apiKey, String model) {
        return new EmbeddingProperties.GeminiConfig(apiKey, null, model);
    }

    private EmbeddingProperties.OllamaConfig ollama(String baseUrl, String model) {
        return new EmbeddingProperties.OllamaConfig(baseUrl, model);
    }

    private EmbeddingProperties properties(
            EmbeddingProperties.GeminiConfig gemini, EmbeddingProperties.OllamaConfig ollama) {
        return new EmbeddingProperties(gemini, ollama, Duration.ofSeconds(5), Duration.ofSeconds(30), 3072);
    }
}