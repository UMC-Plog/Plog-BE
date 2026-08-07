package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.plog.domain.integration.entity.IntegrationActivityType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.IntegrationResourceType;
import com.plog.domain.integration.entity.ProjectIntegration;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NotionIntegrationResourceCollectorTest {

    private static final String TOKEN = "notion-token";
    private static final String RESOURCE_URL = "https://notion.so/root-page";

    private final ProjectIntegrationService projectIntegrationService = mock(ProjectIntegrationService.class);
    private final IntegrationActivityStoreService activityStoreService = mock(IntegrationActivityStoreService.class);
    private final NotionApiRateLimiter rateLimiter = mock(NotionApiRateLimiter.class);

    @Test
    @DisplayName("Spring 컨텍스트는 운영 생성자로 Notion collector 빈을 생성한다")
    void createsCollectorThroughProductionConstructor() {
        new ApplicationContextRunner()
                .withBean(ProjectIntegrationService.class, () -> projectIntegrationService)
                .withBean(IntegrationActivityStoreService.class, () -> activityStoreService)
                .withBean(NotionApiRateLimiter.class, () -> rateLimiter)
                .withBean(NotionUserResolver.class,
                        () -> mock(NotionUserResolver.class))
                .withUserConfiguration(NotionIntegrationResourceCollector.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(NotionIntegrationResourceCollector.class);
                });
    }

    @Test
    @DisplayName("data source query pagination은 JSON POST body와 Content-Type을 유지한다")
    void queriesDataSourcePagesWithJsonPaginationBody() {
        Fixture fixture = fixture();
        IntegrationResource resource = resource(IntegrationResourceType.NOTION_DATA_SOURCE, "data-source-1");
        CountingContext context = new CountingContext();

        expectDataSource(fixture.server);
        expectDataSourceQuery(fixture.server, """
                {"page_size":100,"result_type":"page"}
                """, """
                {"results":[],"has_more":true,"next_cursor":"cursor-2"}
                """);
        expectDataSourceQuery(fixture.server, """
                {"page_size":100,"result_type":"page","start_cursor":"cursor-2"}
                """, """
                {"results":[],"has_more":false}
                """);

        fixture.collector.collect(resource, context);

        fixture.server.verify();
        assertThat(context.heartbeats).isEqualTo(3);
    }

    @Test
    @DisplayName("page 수집은 block pagination과 자식 block을 순회하며 요청마다 heartbeat를 남긴다")
    void traversesNestedBlocksAndHeartbeatsEveryRequest() {
        Fixture fixture = fixture();
        IntegrationResource resource = resource(IntegrationResourceType.NOTION_PAGE, "root-page");
        CountingContext context = new CountingContext();

        expectPage(fixture.server, "root-page");
        expectBlockChildren(fixture.server, "root-page", """
                {"results":[{"id":"child-block","has_children":true,
                  "created_time":"2026-08-01T10:00:00Z","last_edited_time":"2026-08-01T10:00:00Z",
                  "created_by":{"id":"actor-1","name":"Editor","person":{"email":"editor@example.com"}},
                  "last_edited_by":{"id":"actor-1","name":"Editor","person":{"email":"editor@example.com"}}}],
                 "has_more":true,"next_cursor":"root-cursor-2"}
                """);
        expectBlockChildren(fixture.server, "root-page", "root-cursor-2", """
                {"results":[],"has_more":false}
                """);
        expectComments(fixture.server, "child-block", """
                {"results":[],"has_more":false}
                """);
        expectBlockChildren(fixture.server, "child-block", """
                {"results":[{"id":"grandchild-block","has_children":false,
                  "created_time":"2026-08-01T11:00:00Z","last_edited_time":"2026-08-01T11:00:00Z",
                  "created_by":{"id":"actor-2","name":"Editor2","person":{"email":"editor2@example.com"}},
                  "last_edited_by":{"id":"actor-2","name":"Editor2","person":{"email":"editor2@example.com"}}}],
                 "has_more":false}
                """);
        expectComments(fixture.server, "grandchild-block", """
                {"results":[],"has_more":false}
                """);
        expectComments(fixture.server, "root-page", """
                {"results":[],"has_more":false}
                """);

        fixture.collector.collect(resource, context);

        fixture.server.verify();
        assertThat(context.heartbeats).isEqualTo(7);
        verify(activityStoreService).store(
                eq(resource), eq(IntegrationActivityType.NOTION_BLOCK_SNAPSHOT),
                eq("block:child-block:2026-08-01T10:00:00Z"),
                any(), any(), any(), any(), any(), any());
        verify(activityStoreService).store(
                eq(resource), eq(IntegrationActivityType.NOTION_BLOCK_SNAPSHOT),
                eq("block:grandchild-block:2026-08-01T11:00:00Z"),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("page·block·comment의 같은 partial user는 한 번 조회해 표시 정보를 공유한다")
    void enrichesRepeatedPartialUserOnceAcrossPageBlockAndComment() {
        Fixture fixture = fixture();
        IntegrationResource resource = resource(IntegrationResourceType.NOTION_PAGE, "root-page");
        CountingContext context = new CountingContext();

        fixture.server.expect(requestTo("https://api.notion.com/v1/pages/root-page"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":"root-page","last_edited_time":"2026-08-01T09:30:00Z",
                         "url":"https://notion.so/root-page",
                         "created_by":{"id":"user-1"},"last_edited_by":{"id":"user-1"}}
                        """, MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo("https://api.notion.com/v1/users/user-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(header("Notion-Version", "2026-03-11"))
                .andRespond(withSuccess("""
                        {"object":"user","id":"user-1","type":"person","name":"Sangwan",
                         "person":{"email":"sangwan@example.com"}}
                        """, MediaType.APPLICATION_JSON));
        expectBlockChildren(fixture.server, "root-page", """
                {"results":[{"id":"block-1","has_children":false,
                  "last_edited_time":"2026-08-01T10:00:00Z",
                  "created_by":{"id":"user-1"},"last_edited_by":{"id":"user-1"}}],
                 "has_more":false}
                """);
        expectComments(fixture.server, "block-1", """
                {"results":[{"id":"comment-1","created_time":"2026-08-01T10:10:00Z",
                  "created_by":{"id":"user-1"}}],"has_more":false}
                """);
        expectComments(fixture.server, "root-page", """
                {"results":[],"has_more":false}
                """);

        fixture.collector.collect(resource, context);

        fixture.server.verify();
        assertThat(context.heartbeats).isEqualTo(5);
        verify(activityStoreService).backfillActorDisplayInfo(
                10L, "user-1", "Sangwan", "sangwan@example.com");
        verify(activityStoreService).store(
                eq(resource), eq(IntegrationActivityType.NOTION_PAGE_SNAPSHOT),
                eq("page:root-page:2026-08-01T09:30:00Z"),
                eq("user-1"), eq("Sangwan"), eq("sangwan@example.com"), any(), any(), any());
        verify(activityStoreService).store(
                eq(resource), eq(IntegrationActivityType.NOTION_BLOCK_SNAPSHOT),
                eq("block:block-1:2026-08-01T10:00:00Z"),
                eq("user-1"), eq("Sangwan"), eq("sangwan@example.com"), any(), any(), any());
        verify(activityStoreService).store(
                eq(resource), eq(IntegrationActivityType.NOTION_COMMENT), eq("comment:comment-1"),
                eq("user-1"), eq("Sangwan"), eq("sangwan@example.com"), any(), any(), any());
    }

    @Test
    @DisplayName("comment 수집은 pagination cursor를 따라 모든 comment를 저장한다")
    void collectsPaginatedComments() {
        Fixture fixture = fixture();
        IntegrationResource resource = resource(IntegrationResourceType.NOTION_PAGE, "root-page");
        CountingContext context = new CountingContext();
        NotionWebhookTarget target = new NotionWebhookTarget(
                "comment-1", "comment", "root-page", "page");

        expectComments(fixture.server, "root-page", """
                {"results":[{"id":"comment-1","created_time":"2026-08-01T12:00:00Z",
                  "created_by":{"id":"actor-1","name":"Commenter","person":{"email":"c@example.com"}}}],
                 "has_more":true,"next_cursor":"cursor-2"}
                """);
        fixture.server.expect(requestTo(Matchers.containsString(
                        "https://api.notion.com/v1/comments?block_id=root-page&page_size=100&start_cursor=cursor-2")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess("""
                        {"results":[{"id":"comment-2","created_time":"2026-08-01T13:00:00Z",
                          "created_by":{"id":"actor-2","name":"Reviewer","person":{"email":"r@example.com"}}}],
                         "has_more":false}
                        """, MediaType.APPLICATION_JSON));

        fixture.collector.collectChangedEntity(resource, target, context);

        fixture.server.verify();
        assertThat(context.heartbeats).isEqualTo(2);
        verify(activityStoreService).store(
                eq(resource), eq(IntegrationActivityType.NOTION_COMMENT), eq("comment:comment-1"),
                eq("actor-1"), eq("Commenter"), eq("c@example.com"), any(), eq(RESOURCE_URL), any());
        verify(activityStoreService).store(
                eq(resource), eq(IntegrationActivityType.NOTION_COMMENT), eq("comment:comment-2"),
                eq("actor-2"), eq("Reviewer"), eq("r@example.com"), any(), eq(RESOURCE_URL), any());
    }

    @Test
    @DisplayName("parent traversal도 전달받은 context로 provider 요청 heartbeat를 남긴다")
    void parentTraversalUsesCollectionContext() {
        Fixture fixture = fixture();
        IntegrationResource root = resource(IntegrationResourceType.NOTION_PAGE, "root-page");
        CountingContext context = new CountingContext();
        NotionWebhookTarget target = new NotionWebhookTarget(
                "child-block", "block", "middle-block", "block");

        expectBlockParent(fixture.server, "middle-block", "page", "root-page");

        IntegrationResource found = fixture.collector.findContainingResource(List.of(root), target, context);

        fixture.server.verify();
        assertThat(found).isSameAs(root);
        assertThat(context.heartbeats).isEqualTo(1);
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder)
                .ignoreExpectOrder(true)
                .build();
        given(projectIntegrationService.decryptAccessToken(any(ProjectIntegration.class))).willReturn(TOKEN);
        return new Fixture(
                server,
                new NotionIntegrationResourceCollector(
                        projectIntegrationService, activityStoreService, rateLimiter, builder.build())
        );
    }

    private IntegrationResource resource(IntegrationResourceType type, String providerResourceId) {
        ProjectIntegration integration = mock(ProjectIntegration.class);
        IntegrationResource resource = mock(IntegrationResource.class);
        given(integration.getId()).willReturn(10L);
        given(resource.getProjectIntegration()).willReturn(integration);
        given(resource.getResourceType()).willReturn(type);
        given(resource.getProviderResourceId()).willReturn(providerResourceId);
        given(resource.getResourceUrl()).willReturn(RESOURCE_URL);
        return resource;
    }

    private void expectDataSource(MockRestServiceServer server) {
        server.expect(requestTo("https://api.notion.com/v1/data_sources/data-source-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(header("Notion-Version", "2026-03-11"))
                .andRespond(withSuccess("""
                        {"id":"data-source-1","last_edited_time":"2026-08-01T09:00:00Z",
                         "last_edited_by":{"id":"editor-1","name":"Editor",
                           "person":{"email":"editor@example.com"}},
                         "url":"https://notion.so/data-source-1"}
                        """, MediaType.APPLICATION_JSON));
    }

    private void expectDataSourceQuery(MockRestServiceServer server, String expectedBody, String responseBody) {
        server.expect(requestTo("https://api.notion.com/v1/data_sources/data-source-1/query"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(header("Notion-Version", "2026-03-11"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(expectedBody))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    }

    private void expectPage(MockRestServiceServer server, String pageId) {
        server.expect(requestTo("https://api.notion.com/v1/pages/" + pageId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess("""
                        {"id":"%s","created_time":"2026-08-01T09:00:00Z",
                         "last_edited_time":"2026-08-01T09:30:00Z","url":"https://notion.so/%s",
                         "created_by":{"id":"creator-1","name":"Creator",
                           "person":{"email":"creator@example.com"}},
                         "last_edited_by":{"id":"editor-1","name":"Editor",
                           "person":{"email":"editor@example.com"}}}
                        """.formatted(pageId, pageId), MediaType.APPLICATION_JSON));
    }

    private void expectBlockChildren(MockRestServiceServer server, String blockId, String responseBody) {
        server.expect(requestTo("https://api.notion.com/v1/blocks/" + blockId + "/children?page_size=100"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    }

    private void expectBlockChildren(
            MockRestServiceServer server,
            String blockId,
            String cursor,
            String responseBody
    ) {
        server.expect(requestTo("https://api.notion.com/v1/blocks/" + blockId
                        + "/children?page_size=100&start_cursor=" + cursor))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    }

    private void expectComments(MockRestServiceServer server, String blockId, String responseBody) {
        server.expect(requestTo("https://api.notion.com/v1/comments?block_id=" + blockId + "&page_size=100"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    }

    private void expectBlockParent(MockRestServiceServer server, String blockId, String parentType, String parentId) {
        server.expect(requestTo("https://api.notion.com/v1/blocks/" + blockId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess("""
                        {"id":"%s","parent":{"type":"%s_id","%s_id":"%s"}}
                        """.formatted(blockId, parentType, parentType, parentId), MediaType.APPLICATION_JSON));
    }

    private record Fixture(
            MockRestServiceServer server,
            NotionIntegrationResourceCollector collector
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
            throw new AssertionError("collector must not call enterResource");
        }

        @Override
        public void advance(com.plog.domain.integration.entity.CollectionPhase phase, int itemNumber) {
            throw new AssertionError("collector must not call advance");
        }

        @Override
        public void heartbeat() {
            heartbeats++;
        }
    }
}
