package com.plog.infrastructure.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * plog.llm.* 바인딩.
 * <p>
 * apiKey 를 필수로 두지 않는 것이 중요하다 — 키가 없으면 기동에 실패하는 대신 Stub 으로 폴백한다.
 * 팀원 로컬과 CI 에는 키가 없고, 그 환경에서도 앱은 떠야 하기 때문이다.
 *
 * @param provider        "gemini" 또는 "stub". 알 수 없는 값이면 Stub 으로 폴백한다
 * @param apiKey          프로바이더 API 키. 비어 있으면 Stub 으로 폴백
 * @param model           모델명. 교체가 잦으므로 코드에 박지 않는다
 * @param baseUrl         API 엔드포인트 베이스
 * @param connectTimeout  연결 타임아웃
 * @param readTimeout     응답 타임아웃. LLM 은 수 초~수십 초라 다른 외부 연동(10초)보다 길게 잡는다
 * @param maxOutputTokens 응답 토큰 상한
 * @param temperature     낮을수록 재현성이 높다. 평가 문장이라 기본값을 낮게 둔다
 */
@ConfigurationProperties(prefix = "plog.llm")
public record LlmProperties(
        String provider,
        String apiKey,
        String model,
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxOutputTokens,
        double temperature
) {
    public static final String GEMINI = "gemini";

    public LlmProperties {
        provider = provider == null || provider.isBlank() ? "stub" : provider.trim().toLowerCase();
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(60) : readTimeout;
        maxOutputTokens = maxOutputTokens <= 0 ? 2048 : maxOutputTokens;
        if (temperature < 0.0 || temperature > 1.0) {
            throw new IllegalStateException("plog.llm.temperature 는 0.0~1.0 이어야 합니다.");
        }
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 실제 프로바이더를 쓸 수 있는 설정인지. 아니면 Stub 으로 폴백한다. */
    public boolean isGeminiUsable() {
        return GEMINI.equals(provider) && hasApiKey() && model != null && !model.isBlank();
    }
}
