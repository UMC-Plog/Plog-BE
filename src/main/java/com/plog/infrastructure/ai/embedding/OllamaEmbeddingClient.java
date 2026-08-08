package com.plog.infrastructure.ai.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Ollama(자체 호스팅) 임베딩 API 클라이언트. {@code /api/embeddings} 엔드포인트를 호출한다.
 * 내부 네트워크(EC2 Docker 컨테이너 간)에서만 접근하는 서비스로 운용하므로 인증 헤더가 없다 —
 * Gemini처럼 외부에 노출된 API가 아니다.
 */
@Slf4j
public class OllamaEmbeddingClient implements EmbeddingClient {

    /** 5xx·타임아웃에 한해 1회만 더 시도한다. 4xx(모델 미존재, 잘못된 요청 등)는 다시 해도 같은 결과다. */
    private static final int MAX_ATTEMPTS = 2;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final EmbeddingProperties properties;

    public OllamaEmbeddingClient(EmbeddingProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, RestClient.builder()
                .requestFactory(requestFactory(properties.connectTimeout(), properties.readTimeout())));
    }

    /** 테스트가 MockRestServiceServer 를 물린 builder 를 넘길 수 있도록 분리한 생성자. */
    OllamaEmbeddingClient(EmbeddingProperties properties, ObjectMapper objectMapper, RestClient.Builder builder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
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
                // 4xx 는 재시도해도 같은 결과다 — 잘못된 모델명, 요청 형식 오류 등.
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
        root.put("model", properties.model());
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
        List<Float> vector = new ArrayList<>(embeddingNode.size());
        for (JsonNode value : embeddingNode) {
            vector.add((float) value.asDouble());
        }
        return new EmbeddingResponse(vector, properties.model());
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration connect, Duration read) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect);
        factory.setReadTimeout(read);
        return factory;
    }
}