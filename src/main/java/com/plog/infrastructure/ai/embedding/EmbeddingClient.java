package com.plog.infrastructure.ai.embedding;

/**
 * 임베딩 프로바이더 추상화. 구현체 교체는 {@code plog.embedding.provider} 프로퍼티로 한다.
 * <p>
 * 호출부(활동 로그 임베딩 생성)는 이 인터페이스만 알면 되고, 프로바이더별 인증·요청 포맷은
 * 구현체가 감춘다.
 */
public interface EmbeddingClient {

    /**
     * @throws EmbeddingGenerationException 호출 실패, 타임아웃, 빈 응답 등 모든 실패
     */
    EmbeddingResponse embed(String text);

    /** 실제 임베딩을 생성하는 구현인지. false 면 더미(Stub)라 벡터를 신뢰하면 안 된다. */
    default boolean isRealProvider() {
        return true;
    }
}