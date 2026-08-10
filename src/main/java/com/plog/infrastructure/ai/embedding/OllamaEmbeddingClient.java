package com.plog.infrastructure.ai.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Ollama(자체 호스팅) 임베딩 API 클라이언트. {@code /api/embeddings} 엔드포인트를 호출한다.
 * <p>
 * 지금은 기본으로 쓰지 않는다(운영 EC2가 t3.micro 1GB RAM 프리티어라 자체 호스팅을 얹으면
 * 기존 서비스와 메모리를 다툰다) — Gemini API가 기본 경로다. 나중에 자체 호스팅 여유가
 * 생기면 {@code plog.embedding.ollama.*}만 채워서 다시 쓸 수 있도록 코드를 남겨둔다.
 * <p>
 * 내부 네트워크(EC2 Docker 컨테이너 간)에서만 접근하는 서비스로 운용하므로 인증 헤더가 없다.
 */
@Slf4j
public class OllamaEmbeddingClient implements EmbeddingClient {

    /** 5xx·타임아웃에 한해 1회만 더 시도한다. 429(모델 로딩 중 등 과부하)는 여기 포함하지 않는다. */
    private static final int MAX_ATTEMPTS = 2;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final EmbeddingProperties.OllamaConfig config;

    public OllamaEmbeddingClient(EmbeddingProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, RestClient.builder()
                .requestFactory(requestFactory(properties.connectTimeout(), properties.readTimeout())));
    }

    /** 테스트가 MockRestServiceServer 를 물린 builder 를 넘길 수 있도록 분리한 생성자. */
    OllamaEmbeddingClient(EmbeddingProperties properties, ObjectMapper objectMapper, RestClient.Builder builder) {
        this.config = properties.ollama();
        this.objectMapper = objectMapper;
        this.restClient = builder.baseUrl(config.baseUrl()).build();
    }

    @Override
    public EmbeddingResponse embed(String text) {
        String body = buildRequestBody(text);
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String raw = restClient.post()
                        .uri("/api/embeddings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);
                return parseResponse(raw);
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().value() == 429) {
                    // 과부하/한도 초과 — 같은 창 안에서 즉시 재시도해도 똑같이 막힌다.
                    // 상위(ActivityEmbeddingService)가 배치를 멈추고 다음 스케줄 호출로 넘기게 한다.
                    throw new EmbeddingRateLimitException("Ollama 임베딩 호출 한도를 초과했습니다.", e);
                }
                // 그 외 4xx 는 재시도해도 같은 결과다 — 잘못된 모델명, 요청 형식 오류 등.
                if (e.getStatusCode().is4xxClientError()) {
                    throw new EmbeddingGenerationException(
                            "Ollama 임베딩 호출이 거부되었습니다. status=" + e.getStatusCode(), e);
                }
                lastFailure = e;
            } catch (RestClientException e) {
                // 타임아웃·연결 실패. 일시적일 수 있으므로 재시도 대상.
                lastFailure = e;
            }
            if (attempt < MAX_ATTEMPTS) {
                log.warn("Ollama 임베딩 호출 실패, 재시도합니다({}/{})", attempt, MAX_ATTEMPTS, lastFailure);
            }
        }
        throw new EmbeddingGenerationException("Ollama 임베딩 호출에 실패했습니다.", lastFailure);
    }

    private String buildRequestBody(String text) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.model());
        root.put("prompt", text);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new EmbeddingGenerationException("Ollama 요청 본문 생성에 실패했습니다.", e);
        }
    }

    private EmbeddingResponse parseResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new EmbeddingGenerationException("Ollama 임베딩 응답이 비어 있습니다.");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new EmbeddingGenerationException("Ollama 임베딩 응답을 JSON 으로 읽지 못했습니다.", e);
        }

        JsonNode embeddingNode = root.path("embedding");
        if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
            throw new EmbeddingGenerationException("Ollama 임베딩 응답에 embedding 배열이 없습니다.");
        }
        List<Float> vector = EmbeddingVectorParser.parseFiniteVector(embeddingNode, "Ollama 임베딩 응답");
        return new EmbeddingResponse(vector, config.model());
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration connect, Duration read) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect);
        factory.setReadTimeout(read);
        return factory;
    }
}