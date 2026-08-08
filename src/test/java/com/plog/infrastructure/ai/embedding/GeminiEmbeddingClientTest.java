package com.plog.infrastructure.ai.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiEmbeddingClientTest {

    private static final String URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GeminiEmbeddingClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiEmbeddingClient(properties(), objectMapper, builder);
    }

    @Test
    void 정상_응답에서_벡터와_모델명을_돌려준다() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "key-123"))
                .andRespond(withSuccess(
                        "{\"embedding\":{\"values\":[0.1,0.2,0.3]}}", MediaType.APPLICATION_JSON));

        EmbeddingResponse response = client.embed("테스트 문장");

        assertThat(response.vector()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(response.model()).isEqualTo("gemini-embedding-001");
        server.verify();
    }

    @Test
    void embedding_values가_없으면_예외를_던진다() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed("텍스트"))
                .isInstanceOf(EmbeddingGenerationException.class);
    }

    @Test
    void 사백이십구_응답은_EmbeddingRateLimitException을_던지고_재시도하지_않는다() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.embed("텍스트"))
                .isInstanceOf(EmbeddingRateLimitException.class);
        server.verify(); // 요청이 정확히 1회만 나갔는지 (재시도 안 함) 확인
    }

    @Test
    void 그_외_클라이언트_오류_응답은_재시도하지_않고_바로_예외를_던진다() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.embed("텍스트"))
                .isInstanceOf(EmbeddingGenerationException.class)
                .isNotInstanceOf(EmbeddingRateLimitException.class);
        server.verify();
    }

    @Test
    void 서버_오류_응답은_한_번_더_재시도한다() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"embedding\":{\"values\":[0.5]}}", MediaType.APPLICATION_JSON));

        EmbeddingResponse response = client.embed("텍스트");

        assertThat(response.vector()).containsExactly(0.5f);
        server.verify();
    }

    private EmbeddingProperties properties() {
        return new EmbeddingProperties(
                "gemini",
                "key-123",
                "https://generativelanguage.googleapis.com",
                "gemini-embedding-001",
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                3072
        );
    }
}