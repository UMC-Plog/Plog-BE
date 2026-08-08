package com.plog.infrastructure.ai.embedding;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * plog.embedding.* 바인딩.
 * <p>
 * api-key/model을 필수로 두지 않는 것이 중요하다 — 값이 없으면 기동에 실패하는 대신 Stub 으로
 * 폴백한다. 팀원 로컬과 CI 에는 키가 없고, 그 환경에서도 앱은 떠야 하기 때문이다.
 * <p>
 * provider="gemini"가 기본 운영 경로다. Ollama 자체 호스팅도 지원은 하지만(provider="ollama"),
 * 운영 EC2가 t3.micro(1GB RAM) 프리티어라 기본값으로 쓰지 않는다 — Ollama+모델을 얹으면
 * 기존 서비스와 메모리를 다퉈 OOM 위험이 있다.
 *
 * @param provider       "gemini", "ollama", 또는 "stub". 알 수 없는 값이면 Stub 으로 폴백한다
 * @param apiKey         Gemini API 키. LLM과 같은 GEMINI_API_KEY를 재사용한다. 비어 있으면
 *                       (provider=gemini일 때) Stub 으로 폴백
 * @param baseUrl        API 베이스 URL. Gemini 기본값은 공식 엔드포인트, Ollama를 쓰려면
 *                       자체 호스팅 주소로 오버라이드해야 한다
 * @param model          임베딩 모델명(예: gemini-embedding-001, bge-m3). 비어 있으면 Stub 으로 폴백
 * @param connectTimeout 연결 타임아웃
 * @param readTimeout    응답 타임아웃
 * @param dimension      기대하는 임베딩 차원 수. Stub이 더미 벡터를 만들 때 이 길이를 쓴다
 */
@ConfigurationProperties(prefix = "plog.embedding")
public record EmbeddingProperties(
        String provider,
        String apiKey,
        String baseUrl,
        String model,
        Duration connectTimeout,
        Duration readTimeout,
        int dimension
) {
    public static final String GEMINI = "gemini";
    public static final String OLLAMA = "ollama";

    public EmbeddingProperties {
        provider = provider == null || provider.isBlank() ? "stub" : provider.trim().toLowerCase();
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
        dimension = dimension <= 0 ? 3072 : dimension;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Gemini API를 쓸 수 있는 설정인지. 아니면 Ollama나 Stub 으로 폴백한다. */
    public boolean isGeminiUsable() {
        return GEMINI.equals(provider) && hasApiKey() && model != null && !model.isBlank();
    }

    /** Ollama를 쓸 수 있는 설정인지. 아니면 Stub 으로 폴백한다. */
    public boolean isOllamaUsable() {
        return OLLAMA.equals(provider)
                && baseUrl != null && !baseUrl.isBlank()
                && model != null && !model.isBlank();
    }
}