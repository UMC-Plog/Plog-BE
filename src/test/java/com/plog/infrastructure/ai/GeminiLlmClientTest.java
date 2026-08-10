package com.plog.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

class GeminiLlmClientTest {

    private static final String URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GeminiLlmClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiLlmClient(properties(), objectMapper, builder);
    }

    @Test
    void sendsApiKeyHeaderAndStructuredOutputConfig() throws Exception {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "key-123"))
                .andRespond(withSuccess(successBody("{\"headline\":\"한 줄\"}"), MediaType.APPLICATION_JSON));

        LlmResponse response = client.generate(new LlmRequest(
                "system prompt", "user prompt", "{\"type\":\"object\"}", 1024, 0.3));

        assertThat(response.text()).isEqualTo("{\"headline\":\"한 줄\"}");
        assertThat(response.inputTokens()).isEqualTo(120);
        assertThat(response.outputTokens()).isEqualTo(45);
        assertThat(response.totalTokens()).isEqualTo(165);
        server.verify();
    }

    @Test
    void buildsRequestBodyWithSystemInstructionAndSchema() throws Exception {
        String body = (String) ReflectionTestUtils.invokeMethod(
                client,
                "buildRequestBody",
                new LlmRequest("system prompt", "user prompt", "{\"type\":\"object\"}", 1024, 0.3)
        );

        JsonNode root = objectMapper.readTree(body);
        assertThat(root.at("/systemInstruction/parts/0/text").asText()).isEqualTo("system prompt");
        assertThat(root.at("/contents/0/parts/0/text").asText()).isEqualTo("user prompt");
        assertThat(root.at("/generationConfig/responseMimeType").asText()).isEqualTo("application/json");
        assertThat(root.at("/generationConfig/responseSchema/type").asText()).isEqualTo("object");
        assertThat(root.at("/generationConfig/maxOutputTokens").asInt()).isEqualTo(1024);
    }

    @Test
    void omitsResponseSchemaWhenNotRequested() throws Exception {
        String body = (String) ReflectionTestUtils.invokeMethod(
                client, "buildRequestBody", new LlmRequest(null, "user prompt", null, 1024, 0.3));

        JsonNode root = objectMapper.readTree(body);
        assertThat(root.has("systemInstruction")).isFalse();
        assertThat(root.at("/generationConfig").has("responseSchema")).isFalse();
    }

    @Test
    void omitsDeprecatedTemperatureForGemini3() throws Exception {
        GeminiLlmClient gemini3 = new GeminiLlmClient(
                properties("gemini-3.5-flash-lite"), objectMapper, RestClient.builder());

        String body = (String) ReflectionTestUtils.invokeMethod(
                gemini3, "buildRequestBody",
                new LlmRequest("system", "user", null, 1024, 0.3));

        JsonNode root = objectMapper.readTree(body);
        assertThat(root.at("/generationConfig").has("temperature")).isFalse();
        assertThat(root.at("/generationConfig/maxOutputTokens").asInt()).isEqualTo(1024);
    }

    @Test
    void omitsSamplingParametersForFutureGeminiModels() throws Exception {
        GeminiLlmClient futureModel = new GeminiLlmClient(
                properties("gemini-4-flash"), objectMapper, RestClient.builder());

        String body = (String) ReflectionTestUtils.invokeMethod(
                futureModel, "buildRequestBody",
                new LlmRequest("system", "user", null, 1024, 0.3));

        JsonNode root = objectMapper.readTree(body);
        assertThat(root.at("/generationConfig").has("temperature")).isFalse();
    }

    @Test
    void includesTemperatureOnlyForVerifiedGemini2Models() throws Exception {
        String body = (String) ReflectionTestUtils.invokeMethod(
                client, "buildRequestBody",
                new LlmRequest("system", "user", null, 1024, 0.3));

        JsonNode root = objectMapper.readTree(body);
        assertThat(root.at("/generationConfig/temperature").asDouble()).isEqualTo(0.3);
    }

    // 5xx 는 일시적일 수 있으므로 1회 재시도한다.
    @Test
    void retriesOnceOnServerError() {
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withServerError());
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withSuccess(successBody("{\"headline\":\"한 줄\"}"), MediaType.APPLICATION_JSON));

        LlmResponse response = client.generate(request());

        assertThat(response.text()).contains("headline");
        server.verify();
    }

    @Test
    void failsAfterTheRetryIsAlsoExhausted() {
        server.expect(ExpectedCount.twice(), requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOf(LlmGenerationException.class);
        server.verify();
    }

    // 4xx(키 오류·쿼터 초과)는 다시 해도 같은 결과라 재시도하지 않는다 — 쿼터를 더 태우면 안 된다.
    @Test
    void doesNotRetryOnClientError() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOf(LlmGenerationException.class)
                .hasMessageContaining("429");
        server.verify();
    }

    // 잘린 응답은 뒤에서 "이상한 파싱 오류"로 보이므로 여기서 원인을 밝힌다.
    @Test
    void reportsTokenLimitTruncationExplicitly() {
        String body = """
                {"candidates":[{"finishReason":"MAX_TOKENS",
                 "content":{"parts":[{"text":"{\\"headline\\":\\"잘린"}]}}]}
                """;
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOf(LlmGenerationException.class)
                .hasMessageContaining("max-output-tokens");
    }

    @Test
    void reportsSafetyBlockExplicitly() {
        String body = "{\"candidates\":[],\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}";
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOf(LlmGenerationException.class)
                .hasMessageContaining("SAFETY");
    }

    @Test
    void rejectsMalformedProviderResponse() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("not json at all", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOf(LlmGenerationException.class);
    }

    private LlmRequest request() {
        return new LlmRequest("system", "user", null, 1024, 0.3);
    }

    private String successBody(String text) {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
        return """
                {
                  "candidates": [{"finishReason":"STOP","content":{"parts":[{"text":"%s"}]}}],
                  "usageMetadata": {"promptTokenCount":120,"candidatesTokenCount":45},
                  "modelVersion": "gemini-2.5-flash"
                }
                """.formatted(escaped);
    }

    private LlmProperties properties() {
        return properties("gemini-2.5-flash");
    }

    private LlmProperties properties(String model) {
        return new LlmProperties(
                "gemini",
                "key-123",
                model,
                "https://generativelanguage.googleapis.com",
                Duration.ofSeconds(5),
                Duration.ofSeconds(60),
                2048,
                0.3
        );
    }
}
