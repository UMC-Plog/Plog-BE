package com.plog.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 클라이언트 선택. 프로바이더를 갈아끼우는 지점이 여기 하나다.
 * <p>
 * 핵심 규칙: <b>키가 없다고 기동에 실패하지 않는다.</b> 팀원 로컬과 CI 에는 키가 없고
 * 그 환경에서도 앱은 떠야 한다. 대신 Stub 이 선택됐다는 사실을 기동 로그에 남겨서,
 * 운영에서 키가 빠진 채 배포되는 상황이 조용히 지나가지 않게 한다.
 */
@Slf4j
@Configuration
public class LlmClientConfig {

    @Bean
    public LlmClient llmClient(LlmProperties properties, ObjectMapper objectMapper) {
        if (properties.isGeminiUsable()) {
            log.info("LLM 프로바이더: Gemini (model={})", properties.model());
            return new GeminiLlmClient(properties, objectMapper);
        }
        if (LlmProperties.GEMINI.equals(properties.provider())) {
            log.warn("plog.llm.provider=gemini 이지만 API 키 또는 모델명이 없어 Stub 으로 폴백합니다. "
                    + "리포트 텍스트가 더미로 생성됩니다 — 운영이라면 GEMINI_API_KEY 를 확인하세요.");
        } else {
            log.info("LLM 프로바이더: Stub (plog.llm.provider={})", properties.provider());
        }
        return new StubLlmClient(StubLlmResponses.reportJson());
    }
}
