package com.plog.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Gemini Developer API(AI Studio) 클라이언트. API 키 헤더 하나로 인증한다.
 * <p>
 * SDK 를 쓰지 않는 이유: 컨테이너에 Firebase 용 {@code GOOGLE_APPLICATION_CREDENTIALS} 가 이미
 * 주입돼 있어서, ADC 를 자동으로 집는 SDK 는 <b>Firebase 서비스 계정으로 인증을 시도</b>한다.
 * 명시적인 API 키 헤더 방식이면 그 혼선이 원천 차단되고, 기존 외부 연동과 같은 RestClient
 * 컨벤션도 그대로 쓸 수 있다.
 */
@Slf4j
public class GeminiLlmClient implements LlmClient {

    private static final String API_KEY_HEADER = "x-goog-api-key";
    /** 5xx·타임아웃에 한해 1회만 더 시도한다. 4xx(키·쿼터·스키마 오류)는 다시 해도 같은 결과다. */
    private static final int MAX_ATTEMPTS = 2;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final LlmProperties properties;

    public GeminiLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, RestClient.builder()
                .requestFactory(requestFactory(properties.connectTimeout(), properties.readTimeout())));
    }

    /** 테스트가 MockRestServiceServer 를 물린 builder 를 넘길 수 있도록 분리한 생성자. */
    GeminiLlmClient(LlmProperties properties, ObjectMapper objectMapper, RestClient.Builder builder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        String body = buildRequestBody(request);
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String raw = restClient.post()
                        .uri("/v1beta/models/{model}:generateContent", properties.model())
                        .header(API_KEY_HEADER, properties.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);
                return parseResponse(raw);
            } catch (RestClientResponseException e) {
                // 4xx 는 재시도해도 같은 결과다 — 키 오류, 쿼터 초과, 스키마 거절 등.
                if (e.getStatusCode().is4xxClientError()) {
                    throw new LlmGenerationException(
                            "Gemini 호출이 거부되었습니다. status=" + e.getStatusCode(), e);
                }
                lastFailure = e;
            } catch (RestClientException e) {
                // 타임아웃·연결 실패. 일시적일 수 있으므로 재시도 대상.
                lastFailure = e;
            }
            if (attempt < MAX_ATTEMPTS) {
                log.warn("Gemini 호출 실패, 재시도합니다({}/{})", attempt, MAX_ATTEMPTS, lastFailure);
            }
        }
        throw new LlmGenerationException("Gemini 호출에 실패했습니다.", lastFailure);
    }

    private String buildRequestBody(LlmRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            root.putObject("systemInstruction")
                    .putArray("parts")
                    .addObject()
                    .put("text", request.systemPrompt());
        }
        root.putArray("contents")
                .addObject()
                .put("role", "user")
                .putArray("parts")
                .addObject()
                .put("text", request.userPrompt());

        ObjectNode generationConfig = root.putObject("generationConfig");
        // 샘플링 파라미터 지원을 확인한 Gemini 2.x에만 전송한다. 미래 모델은 지원 여부를
        // 확인하기 전까지 보수적으로 생략해 새 모델명이 추가돼도 400으로 막히지 않게 한다.
        if (properties.model().startsWith("gemini-2.")) {
            generationConfig.put("temperature", request.temperature());
        }
        generationConfig.put("maxOutputTokens", request.maxOutputTokens());
        if (request.hasResponseSchema()) {
            // 구조화 출력. 이게 있으면 코드펜스·설명문이 섞여 나오는 파싱 실패가 구조적으로 사라진다.
            generationConfig.put("responseMimeType", MediaType.APPLICATION_JSON_VALUE);
            try {
                generationConfig.set("responseSchema", objectMapper.readTree(request.responseSchema()));
            } catch (Exception e) {
                throw new LlmGenerationException("응답 스키마가 올바른 JSON 이 아닙니다.", e);
            }
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new LlmGenerationException("Gemini 요청 본문 생성에 실패했습니다.", e);
        }
    }

    private LlmResponse parseResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new LlmGenerationException("Gemini 응답이 비어 있습니다.");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new LlmGenerationException("Gemini 응답을 JSON 으로 읽지 못했습니다.", e);
        }

        JsonNode candidate = root.path("candidates").path(0);
        // maxOutputTokens 에 걸려 잘리면 본문이 불완전한 JSON 이라 뒤에서 파싱이 깨진다.
        // 여기서 원인을 밝혀 두지 않으면 "이상한 파싱 오류"로만 보인다.
        String finishReason = candidate.path("finishReason").asText("");
        if ("MAX_TOKENS".equals(finishReason)) {
            throw new LlmGenerationException(
                    "Gemini 응답이 토큰 상한에 걸려 잘렸습니다. plog.llm.max-output-tokens 를 늘리세요.");
        }
        String text = candidate.path("content").path("parts").path(0).path("text").asText(null);
        if (text == null || text.isBlank()) {
            String blockReason = root.path("promptFeedback").path("blockReason").asText("");
            throw new LlmGenerationException(blockReason.isBlank()
                    ? "Gemini 응답에 본문이 없습니다."
                    : "Gemini 가 요청을 차단했습니다. reason=" + blockReason);
        }

        JsonNode usage = root.path("usageMetadata");
        return new LlmResponse(
                text,
                root.path("modelVersion").asText(properties.model()),
                usage.path("promptTokenCount").asInt(0),
                usage.path("candidatesTokenCount").asInt(0)
        );
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration connect, Duration read) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect);
        factory.setReadTimeout(read);
        return factory;
    }
}
