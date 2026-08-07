package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.plog.domain.integration.entity.CollectionPhase;
import com.plog.domain.integration.entity.IntegrationActivityType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.ProjectIntegration;
import java.util.ArrayList;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FigmaIntegrationResourceCollectorTest {

    private static final String TOKEN = "figma-token";
    private static final String FILE_KEY = "figma-file-1";
    private static final String RESOURCE_URL = "https://www.figma.com/design/figma-file-1/App";

    private final ProjectIntegrationService projectIntegrationService = mock(ProjectIntegrationService.class);
    private final IntegrationActivityStoreService activityStoreService =
            mock(IntegrationActivityStoreService.class);

    @Test
    @DisplayName("Spring 컨텍스트는 운영 생성자로 Figma collector 빈을 생성한다")
    void createsCollectorThroughProductionConstructor() {
        new ApplicationContextRunner()
                .withBean(ProjectIntegrationService.class, () -> projectIntegrationService)
                .withBean(IntegrationActivityStoreService.class, () -> activityStoreService)
                .withUserConfiguration(FigmaIntegrationResourceCollector.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FigmaIntegrationResourceCollector.class);
                });
    }

    @Test
    @DisplayName("Figma provider 호출마다 heartbeat를 남기고 version pagination 요청도 포함한다")
    void reportsHeartbeatPerProviderRequestAcrossVersionPagination() {
        Fixture fixture = fixture();
        IntegrationResource resource = resource();
        RecordingContext context = new RecordingContext();

        expectFile(fixture.server);
        expectVersions(fixture.server, "https://api.figma.com/v1/files/" + FILE_KEY + "/versions", """
                {"versions":[{"id":"version-1","created_at":"2026-08-01T10:00:00Z",
                  "user":{"id":"user-1","handle":"Designer","email":"designer@example.com"}}],
                 "pagination":{"next_page":"https://api.figma.com/v1/files/figma-file-1/versions?page=2"}}
                """);
        expectVersions(fixture.server, "https://api.figma.com/v1/files/" + FILE_KEY + "/versions?page=2", """
                {"versions":[{"id":"version-2","created_at":"2026-08-01T11:00:00Z",
                  "user":{"id":"user-2","handle":"Reviewer","email":"reviewer@example.com"}}],
                 "pagination":{}}
                """);
        expectComments(fixture.server);

        fixture.collector.collect(resource, context);

        fixture.server.verify();
        assertThat(fixture.requestedUris).hasSize(4);
        assertThat(context.heartbeats).isEqualTo(fixture.requestedUris.size());
        assertThat(storedTypes()).containsExactly(
                IntegrationActivityType.FIGMA_FILE_METADATA,
                IntegrationActivityType.FIGMA_FILE_VERSION,
                IntegrationActivityType.FIGMA_FILE_VERSION,
                IntegrationActivityType.FIGMA_COMMENT,
                IntegrationActivityType.FIGMA_COMMENT_REACTION
        );
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        List<String> requestedUris = new ArrayList<>();
        builder.requestInterceptor((request, body, execution) -> {
            requestedUris.add(request.getURI().toString());
            return execution.execute(request, body);
        });
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        given(projectIntegrationService.decryptAccessToken(any(ProjectIntegration.class))).willReturn(TOKEN);
        return new Fixture(
                server,
                new FigmaIntegrationResourceCollector(projectIntegrationService, activityStoreService, builder.build()),
                requestedUris
        );
    }

    private void expectFile(MockRestServiceServer server) {
        server.expect(requestTo("https://api.figma.com/v1/files/" + FILE_KEY + "?depth=1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess("""
                        {"name":"App","version":"file-version-1","lastModified":"2026-08-01T09:00:00Z"}
                        """, MediaType.APPLICATION_JSON));
    }

    private void expectVersions(MockRestServiceServer server, String url, String responseBody) {
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    }

    private void expectComments(MockRestServiceServer server) {
        server.expect(requestTo(Matchers.containsString("/v1/files/" + FILE_KEY + "/comments")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess("""
                        {"comments":[{"id":"comment-1","created_at":"2026-08-01T12:00:00Z",
                          "user":{"id":"user-3","handle":"Commenter","email":"commenter@example.com"},
                          "message":"Looks good",
                          "reactions":[{"emoji":"+1","created_at":"2026-08-01T12:05:00Z",
                            "user":{"id":"user-4","handle":"Reactor","email":"reactor@example.com"}}]}]}
                        """, MediaType.APPLICATION_JSON));
    }

    private IntegrationResource resource() {
        ProjectIntegration integration = mock(ProjectIntegration.class);
        IntegrationResource resource = mock(IntegrationResource.class);
        given(resource.getId()).willReturn(220L);
        given(resource.getProjectIntegration()).willReturn(integration);
        given(resource.getProviderResourceId()).willReturn(FILE_KEY);
        given(resource.getResourceUrl()).willReturn(RESOURCE_URL);
        return resource;
    }

    private List<IntegrationActivityType> storedTypes() {
        ArgumentCaptor<IntegrationActivityType> captor = ArgumentCaptor.forClass(IntegrationActivityType.class);
        verify(activityStoreService, atLeastOnce()).store(
                any(), captor.capture(), any(), any(), any(), any(), any(), any(), any());
        return captor.getAllValues();
    }

    private record Fixture(
            MockRestServiceServer server,
            FigmaIntegrationResourceCollector collector,
            List<String> requestedUris
    ) {
    }

    private static final class RecordingContext implements CollectionContext {

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
        public void advance(CollectionPhase phase, int itemNumber) {
            throw new AssertionError("collector must not call advance");
        }

        @Override
        public void heartbeat() {
            heartbeats++;
        }
    }
}
