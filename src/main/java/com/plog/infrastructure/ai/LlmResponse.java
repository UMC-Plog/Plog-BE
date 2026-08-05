package com.plog.infrastructure.ai;

/**
 * 프로바이더 중립 LLM 응답.
 *
 * @param text         응답 본문. 구조화 출력을 요청했으면 JSON 문자열이다
 * @param model        실제로 응답한 모델명. 프로퍼티의 모델명과 다를 수 있어(별칭·자동 업그레이드)
 *                     리포트에 그대로 기록해 두면 나중에 "왜 문장 톤이 바뀌었나"를 추적할 수 있다
 * @param inputTokens  입력 토큰 수. 알 수 없으면 0
 * @param outputTokens 출력 토큰 수. 알 수 없으면 0
 */
public record LlmResponse(
        String text,
        String model,
        int inputTokens,
        int outputTokens
) {
    public LlmResponse {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
    }

    public int totalTokens() {
        return inputTokens + outputTokens;
    }
}
