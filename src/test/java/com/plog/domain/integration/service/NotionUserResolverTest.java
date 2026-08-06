package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.integration.entity.CollectionPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class NotionUserResolverTest {

    private static final String TOKEN = "notion-token";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IntegrationActivityStoreService activityStoreService =
            mock(IntegrationActivityStoreService.class);
    private final NotionApiRateLimiter rateLimiter = mock(NotionApiRateLimiter.class);

    @Test
    @DisplayName("Spring 컨텍스트는 운영 생성자로 Notion user resolver 빈을 생성한다")
    void createsResolverThroughProductionConstructor() {
        new ApplicationContextRunner()
                .withBean(IntegrationActivityStoreService.class, () -> activityStoreService)
                .withBean(NotionApiRateLimiter.class, () -> rateLimiter)
                .withUserConfiguration(NotionUserResolver.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(NotionUserResolver.class);
                });
    }

    @Test
    @DisplayName("같은 수집에서 같은 partial user는 한 번만 조회하고 기존 actor 표시 정보를 보충한다")
    void resolvesSamePartialUserOnceAndBackfillsExistingActivities() throws Exception {
        Fixture fixture = fixture();
        NotionUserResolver.Session session = fixture.resolver.begin(10L, TOKEN, fixture.context);
        JsonNode partial = objectMapper.readTree("""
                {"object":"user","id":"user-1","type":"person"}
                """);
        fixture.server.expect(requestTo("https://api.notion.com/v1/users/user-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(header("Notion-Version", "2026-03-11"))
                .andRespond(withSuccess("""
                        {"object":"user","id":"user-1","type":"person","name":"Sangwan",
                         "person":{"email":"sangwan@example.com"}}
                        """, MediaType.APPLICATION_JSON));

        NotionUserResolver.Actor first = fixture.resolver.resolve(session, partial);
        NotionUserResolver.Actor second = fixture.resolver.resolve(session, partial);

        fixture.server.verify();
        assertThat(first).isEqualTo(new NotionUserResolver.Actor(
                "user-1", "Sangwan", "sangwan@example.com"));
        assertThat(second).isEqualTo(first);
        assertThat(fixture.context.heartbeats).isOne();
        verify(activityStoreService).backfillActorDisplayInfo(
                10L, "user-1", "Sangwan", "sangwan@example.com");
    }

    @Test
    @DisplayName("user-information capability가 없는 403은 actor ID만 유지하고 수집을 계속한다")
    void keepsPartialActorWhenUserCapabilityIsForbidden() throws Exception {
        Fixture fixture = fixture();
        NotionUserResolver.Session session = fixture.resolver.begin(10L, TOKEN, fixture.context);
        JsonNode partial = objectMapper.readTree("""
                {"object":"user","id":"user-1","type":"person"}
                """);
        fixture.server.expect(requestTo("https://api.notion.com/v1/users/user-1"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"restricted_resource\"}"));

        NotionUserResolver.Actor first = fixture.resolver.resolve(session, partial);
        NotionUserResolver.Actor second = fixture.resolver.resolve(session, partial);

        fixture.server.verify();
        assertThat(first).isEqualTo(new NotionUserResolver.Actor("user-1", null, null));
        assertThat(second).isEqualTo(first);
        assertThat(fixture.context.heartbeats).isOne();
        verify(activityStoreService, never()).backfillActorDisplayInfo(
                10L, "user-1", null, null);
    }

    @Test
    @DisplayName("Notion user 조회의 일시적 네트워크 장애는 본 수집을 실패시키지 않는다")
    void keepsPartialActorWhenUserLookupHasNetworkFailure() throws Exception {
        Fixture fixture = fixture();
        NotionUserResolver.Session session = fixture.resolver.begin(10L, TOKEN, fixture.context);
        JsonNode partial = objectMapper.readTree("""
                {"object":"user","id":"user-1","type":"person","name":"Partial Name"}
                """);
        fixture.server.expect(requestTo("https://api.notion.com/v1/users/user-1"))
                .andRespond(request -> {
                    throw new ResourceAccessException("connection reset");
                });

        NotionUserResolver.Actor actor = fixture.resolver.resolve(session, partial);

        fixture.server.verify();
        assertThat(actor).isEqualTo(new NotionUserResolver.Actor("user-1", "Partial Name", null));
        assertThat(fixture.context.heartbeats).isOne();
        verify(activityStoreService).backfillActorDisplayInfo(
                10L, "user-1", "Partial Name", null);
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 503})
    @DisplayName("Notion user 조회의 rate limit과 provider 장애도 본 수집을 실패시키지 않는다")
    void keepsPartialActorWhenUserLookupReturnsTemporaryFailure(int status) throws Exception {
        Fixture fixture = fixture();
        NotionUserResolver.Session session = fixture.resolver.begin(10L, TOKEN, fixture.context);
        JsonNode partial = objectMapper.readTree("""
                {"object":"user","id":"user-1","type":"person"}
                """);
        fixture.server.expect(requestTo("https://api.notion.com/v1/users/user-1"))
                .andRespond(withStatus(HttpStatusCode.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"temporary_failure\"}"));

        NotionUserResolver.Actor actor = fixture.resolver.resolve(session, partial);

        fixture.server.verify();
        assertThat(actor).isEqualTo(new NotionUserResolver.Actor("user-1", null, null));
        assertThat(fixture.context.heartbeats).isOne();
    }

    @Test
    @DisplayName("payload에 완전한 user 정보가 있으면 추가 API 호출 없이 기존 행만 보충한다")
    void usesCompletePayloadWithoutUserLookup() throws Exception {
        Fixture fixture = fixture();
        NotionUserResolver.Session session = fixture.resolver.begin(10L, TOKEN, fixture.context);
        JsonNode complete = objectMapper.readTree("""
                {"object":"user","id":"user-1","type":"person","name":"Sangwan",
                 "person":{"email":"sangwan@example.com"}}
                """);

        NotionUserResolver.Actor actor = fixture.resolver.resolve(session, complete);

        fixture.server.verify();
        assertThat(actor).isEqualTo(new NotionUserResolver.Actor(
                "user-1", "Sangwan", "sangwan@example.com"));
        assertThat(fixture.context.heartbeats).isZero();
        verifyNoInteractions(rateLimiter);
        verify(activityStoreService).backfillActorDisplayInfo(
                10L, "user-1", "Sangwan", "sangwan@example.com");
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CountingContext context = new CountingContext();
        return new Fixture(
                server,
                new NotionUserResolver(activityStoreService, rateLimiter, builder.build()),
                context
        );
    }

    private record Fixture(
            MockRestServiceServer server,
            NotionUserResolver resolver,
            CountingContext context
    ) {
    }

    private static final class CountingContext implements CollectionContext {

        private int heartbeats;

        @Override
        public CollectionCursor cursor() {
            return CollectionCursor.start();
        }

        @Override
        public void enterResource(Long resourceId) {
            throw new AssertionError("resolver must not call enterResource");
        }

        @Override
        public void advance(CollectionPhase phase, int itemNumber) {
            throw new AssertionError("resolver must not call advance");
        }

        @Override
        public void heartbeat() {
            heartbeats++;
        }
    }
}
