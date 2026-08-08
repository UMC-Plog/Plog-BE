package com.plog.infrastructure.ai.embedding;

/**
 * 임베딩 호출·응답 처리 실패. 프로바이더별 예외(RestClient, Jackson 등)를 이 하나로 감싸서
 * 호출부가 프로바이더를 몰라도 되게 한다.
 * 임베딩 배치 처리 중 이 예외가 나면 해당 활동 로그만 실패로 남기고(embeddingModel이
 * 계속 null이라 다음 배치에서 재시도된다) 나머지는 계속 진행한다.
 */
public class EmbeddingGenerationException extends RuntimeException {

    public EmbeddingGenerationException(String message) {
        super(message);
    }

    public EmbeddingGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}