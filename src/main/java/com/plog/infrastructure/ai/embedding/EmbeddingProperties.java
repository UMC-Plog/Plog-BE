package com.plog.infrastructure.ai.embedding;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * plog.embedding.* 바인딩.
 * <p>
 * 프로바이더별 설정(gemini/ollama)을 완전히 분리해뒀다 — {@link #isGeminiUsable()}과
 * {@link #isOllamaUsable()}은 서로 다른 필드만 보고 각자 독립적으로 판단한다. 그래서
 * {@link EmbeddingClientConfig}가 "Gemini 되면 Gemini, 안 되면 Ollama, 그것도 안 되면
 * Stub" 순서로 가용성만 보고 순차 선택할 수 있다 — provider 문자열로 서로를 배타적으로
 * gating하지 않는다(그렇게 하면 Gemini 키가 비었을 때 Ollama 설정이 있어도 절대 못
 * 쓰는 모순이 생긴다).
 * <p>
 * ollama.base-url/model 기본값을 비워둔 것은 의도적이다 — localhost 같은 그럴듯한 기본값을
 * 주면 운영에서 아무도 설정 안 했는데도 "설정된 것처럼" 보여서 위험하다. Ollama를 실제로
 * 쓰려면 두 값을 명시적으로 다 채워야만 usable 로 판정된다.
 *
 * @param gemini         Gemini API 설정. api-key/model이 있어야 usable
 * @param ollama         Ollama 자체 호스팅 설정. base-url/model이 있어야 usable (기본은 비어 있어 미사용)
 * @param connectTimeout 연결 타임아웃 (프로바이더 공통)
 * @param readTimeout    응답 타임아웃 (프로바이더 공통)
 * @param dimension      기대하는 임베딩 차원 수. Stub이 더미 벡터를 만들 때 이 길이를 쓴다
 */
@ConfigurationProperties(prefix = "plog.embedding")
public record EmbeddingProperties(
        GeminiConfig gemini,
        OllamaConfig ollama,
        Duration connectTimeout,
        Duration readTimeout,
        int dimension
) {
    public EmbeddingProperties {
        gemini = gemini == null ? new GeminiConfig(null, null, null) : gemini;
        ollama = ollama == null ? new OllamaConfig(null, null) : ollama;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
        dimension = dimension <= 0 ? 3072 : dimension;
    }

    /** Gemini API를 쓸 수 있는 설정인지. 아니면 Ollama나 Stub 으로 폴백한다. */
    public boolean isGeminiUsable() {
        return hasText(gemini.apiKey()) && hasText(gemini.model());
    }

    /** Ollama를 쓸 수 있는 설정인지. 아니면 Stub 으로 폴백한다. */
    public boolean isOllamaUsable() {
        return hasText(ollama.baseUrl()) && hasText(ollama.model());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record GeminiConfig(String apiKey, String baseUrl, String model) {
        public GeminiConfig {
            baseUrl = hasText(baseUrl) ? baseUrl : "https://generativelanguage.googleapis.com";
        }
    }

    public record OllamaConfig(String baseUrl, String model) {
    }
}