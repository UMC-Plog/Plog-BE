package com.plog.infrastructure.ai;

import lombok.extern.slf4j.Slf4j;

/**
 * API 키 없이도 리포트 파이프라인을 끝까지 돌려보기 위한 더미 클라이언트.
 * 로컬·CI 기본값이며, 키가 비어 있을 때의 폴백이기도 하다.
 * <p>
 * 요청에 스키마가 실려 오면 그 스키마의 예시 응답을 그대로 돌려준다 —
 * 호출부의 파서와 후처리가 실제 프로바이더 없이도 검증되게 하려는 것이다.
 */
@Slf4j
public class StubLlmClient implements LlmClient {

    private final String cannedJson;

    public StubLlmClient(String cannedJson) {
        this.cannedJson = cannedJson;
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        log.warn("StubLlmClient 사용 중 — 실제 생성 결과가 아닙니다. "
                + "리포트 텍스트는 고정 더미입니다(plog.llm.provider / GEMINI_API_KEY 확인).");
        return new LlmResponse(cannedJson, "stub", 0, 0);
    }

    @Override
    public boolean isRealProvider() {
        return false;
    }
}
