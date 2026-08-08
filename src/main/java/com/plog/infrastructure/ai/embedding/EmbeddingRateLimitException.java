package com.plog.infrastructure.ai.embedding;

/**
 * 임베딩 API 호출이 분당/일일 한도(429 RESOURCE_EXHAUSTED)에 걸렸을 때 던진다.
 * <p>
 * 일반 {@link EmbeddingGenerationException}과 구분하는 이유: 즉시 재시도해도 같은 시간 창
 * 안에서는 똑같이 막힌다. 호출부(ActivityEmbeddingService)가 이 예외를 보면 남은 배치를
 * 마저 두드리지 않고 거기서 멈춰, 다음 스케줄 호출(시간이 지나 한도가 풀린 뒤)로 넘긴다.
 */
public class EmbeddingRateLimitException extends EmbeddingGenerationException {

    public EmbeddingRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}