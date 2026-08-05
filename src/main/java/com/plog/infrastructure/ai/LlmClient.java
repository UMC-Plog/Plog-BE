package com.plog.infrastructure.ai;

/**
 * LLM 프로바이더 추상화. 구현체 교체는 {@code plog.llm.provider} 프로퍼티로 한다.
 * <p>
 * 호출부(리포트 생성)는 이 인터페이스만 알면 되고, 프로바이더별 인증·요청 포맷·토큰 회계는
 * 구현체가 감춘다.
 */
public interface LlmClient {

    /**
     * @throws LlmGenerationException 호출 실패, 타임아웃, 빈 응답 등 모든 실패
     */
    LlmResponse generate(LlmRequest request);

    /** 실제 텍스트를 생성하는 구현인지. false 면 더미(Stub)라 결과를 신뢰하면 안 된다. */
    default boolean isRealProvider() {
        return true;
    }
}
