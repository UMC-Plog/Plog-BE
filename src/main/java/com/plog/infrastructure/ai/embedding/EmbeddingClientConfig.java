package com.plog.infrastructure.ai.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 임베딩 클라이언트 선택. 프로바이더를 갈아끼우는 지점이 여기 하나다.
 * <p>
 * 순서: Gemini(운영 기본) → Ollama(설정 시에만) → Stub. {@link EmbeddingProperties}의
 * {@code isGeminiUsable()}/{@code isOllamaUsable()}은 서로의 필드를 전혀 참조하지 않는
 * 완전히 독립적인 판단이라, Gemini 키가 비어 있어도 Ollama 설정이 채워져 있으면 그쪽으로
 * 정상적으로 넘어간다.
 * <p>
 * 핵심 규칙: 설정이 없다고 기동에 실패하지 않는다. 팀원 로컬과 CI 에는 키가 없고
 * 그 환경에서도 앱은 떠야 한다. 대신 Stub 이 선택됐다는 사실을 기동 로그에 남겨서,
 * 운영에서 설정이 빠진 채 배포되는 상황이 조용히 지나가지 않게 한다.
 * (운영 배포 자체는 cd.yml에서 GEMINI_API_KEY를 필수 시크릿으로 검증해서, Stub 이 운영에 올라가는 경우는
 * 배포 단계에서 막힌다 — 이 로그는 그 마지막 방어선이다.)
 */

@Slf4j
@Configuration
public class EmbeddingClientConfig {

    @Bean
    public EmbeddingClient embeddingClient(EmbeddingProperties properties, ObjectMapper objectMapper) {
        if (properties.isGeminiUsable()) {
            log.info("임베딩 프로바이더: Gemini (model={})", properties.gemini().model());
            return new GeminiEmbeddingClient(properties, objectMapper);
        }
        if (properties.isOllamaUsable()) {
            log.info("임베딩 프로바이더: Ollama (model={}, baseUrl={})",
                    properties.ollama().model(), properties.ollama().baseUrl());
            return new OllamaEmbeddingClient(properties, objectMapper);
        }
        log.warn("Gemini/Ollama 둘 다 설정이 없어 Stub 으로 폴백합니다. "
                + "임베딩이 더미 벡터로 생성됩니다 — 운영이라면 GEMINI_API_KEY 를 확인하세요.");
        return new StubEmbeddingClient(properties.dimension());
    }
}