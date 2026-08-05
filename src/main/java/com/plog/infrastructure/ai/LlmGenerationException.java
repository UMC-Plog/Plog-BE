package com.plog.infrastructure.ai;

/**
 * LLM 호출·응답 처리 실패. 프로바이더별 예외(RestClient, Jackson 등)를 이 하나로 감싸서
 * 호출부가 프로바이더를 몰라도 되게 한다.
 * <p>
 * 리포트 생성 중 이 예외가 나면 <b>해당 멤버만</b> 실패로 기록하고 나머지 멤버는 계속 진행한다.
 */
public class LlmGenerationException extends RuntimeException {

    public LlmGenerationException(String message) {
        super(message);
    }

    public LlmGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
