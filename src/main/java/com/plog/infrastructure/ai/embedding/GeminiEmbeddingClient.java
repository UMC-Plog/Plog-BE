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
 * Gemini Developer API(AI Studio) 임베딩 클라이언트. {@code gemini-embedding-001}을 쓴다.
 * <p>
 * LLM과 같은 GEMINI_API_KEY, 같은 인증 방식(x-goog-api-key 헤더)이라 별도 시크릿이 필요 없다.
 * 원래 Ollama 자체 호스팅(EC2 Docker 컨테이너)을 검토했으나, 운영 EC2가 t3.micro(1GB RAM)
 * 프리티어라 Ollama+모델을 얹으면 기존 서비스와 메모리를 다퉈 OOM 위험이 있어 외부 API 호출
 * 방식으로 전환했다 — EC2 리소스를 전혀 안 쓴다.
 */
@Slf4j
public class GeminiEmbeddingClient implements EmbeddingClient {

    private static final String API_KEY_HEADER = "x-goog-api-key";
    /** 5xx·타임아웃에 한해 1회만 더 시도한다. 429(한도 초과)는 여기 포함하지 않는다 — 즉시 재시도해도 같다. */
    private static final int MAX_ATTEMPTS = 2;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final EmbeddingProperties properties;

    public GeminiEmbeddingClient(EmbeddingProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, RestClient.builder()
                .requestFactory(requestFactory(properties.connectTimeout(), properties.readTimeout())));
    }

    /** 테스트가 MockRestServiceServer 를 물린 builder 를 넘길 수 있도록 분리한 생성자. */
    GeminiEmbeddingClient(EmbeddingProperties properties, ObjectMapper objectMapper, RestClient.Builder builder) {
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
                        .uri("/v1beta/models/{model}:embedContent", properties.model())
                        .header(API_KEY_HEADER, properties.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);
                return parseResponse(raw);
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().value() == 429) {
                    // 분당/일일 한도 초과 — 같은 창 안에서 즉시 재시도해도 똑같이 막힌다.
                    // 상위(ActivityEmbeddingService)가 배치를 멈추고 다음 스케줄 호출로 넘기게 한다.
                    throw new EmbeddingRateLimitException("Gemini 임베딩 호출 한도를 초과했습니다.", e);
                }
                // 그 외 4xx 는 재시도해도 같은 결과다 — 키 오류, 잘못된 모델명, 요청 형식 오류 등.
                if (e.getStatusCode().is4xxClientError()) {
                    throw new EmbeddingGenerationException(
                            "Gemini 임베딩 호출이 거부되었습니다. status=" + e.getStatusCode(), e);
                }
                lastFailure = e;
            } catch (RestClientException e) {
                // 타임아웃·연결 실패. 일시적일 수 있으므로 재시도 대상.
                lastFailure = e;
            }
            if (attempt < MAX_ATTEMPTS) {
                log.warn("Gemini 임베딩 호출 실패, 재시도합니다({}/{})", attempt, MAX_ATTEMPTS, lastFailure);
            }
        }
        throw new EmbeddingGenerationException("Gemini 임베딩 호출에 실패했습니다.", lastFailure);
    }

    private String buildRequestBody(String text) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", "models/" + properties.model());
        root.putObject("content").putArray("parts").addObject().put("text", text);
        // 저장해두고 나중에 유사도 검색에 쓸 문서 텍스트라 RETRIEVAL_DOCUMENT로 최적화한다.
        root.put("taskType", "RETRIEVAL_DOCUMENT");
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new EmbeddingGenerationException("Gemini 요청 본문 생성에 실패했습니다.", e);
        }
    }

    private EmbeddingResponse parseResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new EmbeddingGenerationException("Gemini 임베딩 응답이 비어 있습니다.");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new EmbeddingGenerationException("Gemini 임베딩 응답을 JSON 으로 읽지 못했습니다.", e);
        }

        JsonNode values = root.path("embedding").path("values");
        if (!values.isArray() || values.isEmpty()) {
            throw new EmbeddingGenerationException("Gemini 임베딩 응답에 embedding.values 배열이 없습니다.");
        }
        List<Float> vector = new ArrayList<>(values.size());
        for (JsonNode value : values) {
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