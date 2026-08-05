package com.plog.infrastructure.ai;

/**
 * 프로바이더 중립 LLM 요청.
 *
 * @param systemPrompt     역할·규칙. 매 호출 동일한 내용이라 프로바이더가 캐시할 수 있는 자리다
 * @param userPrompt       이번 호출의 데이터. <b>원본 로그가 아니라 요약 수치와 선별된 근거만</b> 담는다
 * @param responseSchema   응답 JSON 스키마(OpenAPI subset). null 이면 자유 텍스트.
 *                         값이 있으면 프로바이더가 구조화 출력을 강제해 파싱 실패가 거의 사라진다
 * @param maxOutputTokens  응답 상한
 * @param temperature      0.0~1.0. 평가 문장은 재현성이 중요해 낮게 쓴다
 */
public record LlmRequest(
        String systemPrompt,
        String userPrompt,
        String responseSchema,
        int maxOutputTokens,
        double temperature
) {
    public LlmRequest {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("userPrompt must not be blank");
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
        if (temperature < 0.0 || temperature > 1.0) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 1.0");
        }
    }

    public boolean hasResponseSchema() {
        return responseSchema != null && !responseSchema.isBlank();
    }
}
