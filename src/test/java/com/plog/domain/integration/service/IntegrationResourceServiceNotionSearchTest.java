package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.plog.domain.integration.dto.NotionResourceType;
import com.plog.domain.integration.dto.response.IntegrationResourceCandidateResponse;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.IntegrationActivityRepository;
import com.plog.domain.integration.repository.IntegrationResourceRepository;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.report.service.IntegrationActivityReportLogAdapter;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class IntegrationResourceServiceNotionSearchTest {

    private static final Long PROJECT_ID = 10L;
    private static final Long USER_ID = 20L;
    private static final String TOKEN = "notion-token";

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectAccessService projectAccessService = mock(ProjectAccessService.class);
    private final ProjectIntegrationRepository projectIntegrationRepository = mock(ProjectIntegrationRepository.class);
    private final ProjectIntegrationService projectIntegrationService = mock(ProjectIntegrationService.class);
    private final IntegrationVerificationService integrationVerificationService =
            mock(IntegrationVerificationService.class);
    private final IntegrationResourceRepository integrationResourceRepository =
            mock(IntegrationResourceRepository.class);
    private final IntegrationActivityRepository integrationActivityRepository =
            mock(IntegrationActivityRepository.class);
    private final IntegrationActivityReportLogAdapter reportLogAdapter =
            mock(IntegrationActivityReportLogAdapter.class);
    private final GithubAppClient githubAppClient = mock(GithubAppClient.class);

    @Test
    @DisplayName("Spring 컨텍스트는 운영 생성자로 IntegrationResourceService 빈을 생성한다")
    void createsServiceThroughProductionConstructor() {
        new ApplicationContextRunner()
                .withBean(ProjectRepository.class, () -> projectRepository)
                .withBean(ProjectAccessService.class, () -> projectAccessService)
                .withBean(ProjectIntegrationRepository.class, () -> projectIntegrationRepository)
                .withBean(ProjectIntegrationService.class, () -> projectIntegrationService)
                .withBean(IntegrationVerificationService.class, () -> integrationVerificationService)
                .withBean(IntegrationResourceRepository.class, () -> integrationResourceRepository)
                .withBean(IntegrationActivityRepository.class, () -> integrationActivityRepository)
                .withBean(IntegrationActivityReportLogAdapter.class, () -> reportLogAdapter)
                .withBean(GithubAppClient.class, () -> githubAppClient)
                .withUserConfiguration(IntegrationResourceService.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IntegrationResourceService.class);
                });
    }

    @Test
    @DisplayName("Notion 후보 검색은 JSON Content-Type과 인증 헤더를 보내고 응답을 후보로 매핑한다")
    void searchesNotionCandidatesWithJsonContractAndMapsResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        IntegrationResourceService service = service(builder.build());

        server.expect(requestTo("https://api.notion.com/v1/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(header("Notion-Version", "2026-03-11"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"query":"Plog","page_size":100}
                        """))
                .andRespond(withSuccess("""
                        {"results":[
                          {"object":"page","id":"page-1","url":"https://notion.so/page-1",
                           "last_edited_time":"2026-08-05T12:34:56Z",
                           "properties":{"Name":{"title":[{"plain_text":"Roadmap"}]}}},
                          {"object":"data_source","id":"data-source-1","url":"https://notion.so/source-1",
                           "last_edited_time":"2026-08-05T13:00:00Z",
                           "title":[{"plain_text":"Tasks"}]},
                          {"object":"database","id":"legacy-db"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<IntegrationResourceCandidateResponse> candidates =
                service.getNotionCandidates(PROJECT_ID, USER_ID, "  Plog  ");

        server.verify();
        assertThat(candidates).containsExactly(
                new IntegrationResourceCandidateResponse(
                        "page-1",
                        NotionResourceType.PAGE,
                        "Roadmap",
                        "https://notion.so/page-1",
                        Instant.parse("2026-08-05T12:34:56Z")
                ),
                new IntegrationResourceCandidateResponse(
                        "data-source-1",
                        NotionResourceType.DATA_SOURCE,
                        "Tasks",
                        "https://notion.so/source-1",
                        Instant.parse("2026-08-05T13:00:00Z")
                )
        );
    }

    private IntegrationResourceService service(RestClient restClient) {
        ProjectMember member = mock(ProjectMember.class);
        ProjectIntegration integration = mock(ProjectIntegration.class);
        given(projectRepository.existsById(PROJECT_ID)).willReturn(true);
        given(projectAccessService.requireActiveMember(PROJECT_ID, USER_ID)).willReturn(member);
        given(integrationVerificationService.requireVerifiedConnection(PROJECT_ID, LinkType.NOTION))
                .willReturn(integration);
        given(projectIntegrationService.decryptAccessToken(any(ProjectIntegration.class))).willReturn(TOKEN);
        return new IntegrationResourceService(
                projectRepository,
                projectAccessService,
                projectIntegrationRepository,
                projectIntegrationService,
                integrationVerificationService,
                integrationResourceRepository,
                integrationActivityRepository,
                reportLogAdapter,
                githubAppClient,
                restClient
        );
    }
}
