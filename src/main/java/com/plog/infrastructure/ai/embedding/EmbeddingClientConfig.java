package com.plog.infrastructure.ai.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 임베딩 클라이언트 선택. 프로바이더를 갈아끼우는 지점이 여기 하나다.
 * <p>
 * 우선순위: Gemini(운영 기본) → Ollama(설정 시에만, 자체 호스팅 여유가 생기면 재사용) → Stub.
 * <p>
 * 핵심 규칙: <b>설정이 없다고 기동에 실패하지 않는다.</b> 팀원 로컬과 CI 에는 키가 없고
 * 그 환경에서도 앱은 떠야 한다. 대신 Stub 이 선택됐다는 사실을 기동 로그에 남겨서,
 * 운영에서 설정이 빠진 채 배포되는 상황이 조용히 지나가지 않게 한다.
 */
@Slf4j
@Configuration
public class EmbeddingClientConfig {

    @Bean
    public EmbeddingClient embeddingClient(EmbeddingProperties properties, ObjectMapper objectMapper) {
        if (properties.isGeminiUsable()) {
            log.info("임베딩 프로바이더: Gemini (model={})", properties.model());
            return new GeminiEmbeddingClient(properties, objectMapper);
        }
        if (properties.isOllamaUsable()) {
            log.info("임베딩 프로바이더: Ollama (model={}, baseUrl={})", properties.model(), properties.baseUrl());
            return new OllamaEmbeddingClient(properties, objectMapper);
        }
        if (EmbeddingProperties.GEMINI.equals(properties.provider())) {
            log.warn("plog.embedding.provider=gemini 이지만 api-key 또는 model이 없어 Stub 으로 폴백합니다. "
                    + "임베딩이 더미 벡터로 생성됩니다 — 운영이라면 GEMINI_API_KEY/EMBEDDING_MODEL 을 확인하세요.");
        } else if (EmbeddingProperties.OLLAMA.equals(properties.provider())) {
            log.warn("plog.embedding.provider=ollama 이지만 base-url 또는 model이 없어 Stub 으로 폴백합니다.");
        } else {
            log.info("임베딩 프로바이더: Stub (plog.embedding.provider={})", properties.provider());
        }
        return new StubEmbeddingClient(properties.dimension());
    }
}