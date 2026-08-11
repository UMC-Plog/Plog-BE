package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.plog.domain.integration.dto.request.FigmaResourceRegisterRequest;
import com.plog.domain.integration.dto.response.IntegrationResourceResponse;
import com.plog.domain.integration.entity.IntegrationCredentialType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.IntegrationResourceType;
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
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class IntegrationResourceServiceFigmaTest {

    private static final Long PROJECT_ID = 10L;
    private static final Long USER_ID = 20L;
    private static final String TOKEN = "figma-token";
    private static final String FILE_KEY = "Abc123";
    private static final String FILE_URL = "https://www.figma.com/design/Abc123/Plog";

    private final ProjectRepository projectRepository = org.mockito.Mockito.mock(ProjectRepository.class);
    private final ProjectAccessService projectAccessService = org.mockito.Mockito.mock(ProjectAccessService.class);
    private final ProjectIntegrationRepository projectIntegrationRepository =
            org.mockito.Mockito.mock(ProjectIntegrationRepository.class);
    private final ProjectIntegrationService projectIntegrationService =
            org.mockito.Mockito.mock(ProjectIntegrationService.class);
    private final IntegrationVerificationService integrationVerificationService =
            org.mockito.Mockito.mock(IntegrationVerificationService.class);
    private final IntegrationResourceRepository integrationResourceRepository =
            org.mockito.Mockito.mock(IntegrationResourceRepository.class);
    private final IntegrationActivityRepository integrationActivityRepository =
            org.mockito.Mockito.mock(IntegrationActivityRepository.class);
    private final IntegrationActivityReportLogAdapter reportLogAdapter =
            org.mockito.Mockito.mock(IntegrationActivityReportLogAdapter.class);
    private final GithubAppClient githubAppClient = org.mockito.Mockito.mock(GithubAppClient.class);

    @Test
    @DisplayName("Figma 리소스 등록은 원문 대신 Tier 3 메타데이터로 파일을 검증한다")
    void validatesFigmaResourceWithMetadataEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        IntegrationResourceService service = service(builder.build());

        server.expect(requestTo("https://api.figma.com/v1/files/" + FILE_KEY + "/meta"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess("""
                        {"file":{"name":"Plog","version":"version-7",
                          "last_touched_at":"2026-08-11T17:00:00Z",
                          "url":"https://www.figma.com/design/Abc123/Plog"}}
                        """, MediaType.APPLICATION_JSON));

        IntegrationResourceResponse response = service.registerFigma(
                PROJECT_ID, USER_ID, new FigmaResourceRegisterRequest(FILE_URL));

        server.verify();
        assertThat(response.providerResourceId()).isEqualTo(FILE_KEY);
        assertThat(response.resourceType()).isEqualTo(IntegrationResourceType.FIGMA_FILE);
        assertThat(response.resourceName()).isEqualTo("Plog");
        assertThat(response.lastModifiedAt()).isEqualTo(Instant.parse("2026-08-11T17:00:00Z"));

        ArgumentCaptor<IntegrationResource> resourceCaptor = ArgumentCaptor.forClass(IntegrationResource.class);
        org.mockito.Mockito.verify(integrationResourceRepository).saveAndFlush(resourceCaptor.capture());
        assertThat(resourceCaptor.getValue().getProviderMetadata())
                .contains("\"version\":\"version-7\"")
                .doesNotContain("document");
    }

    private IntegrationResourceService service(RestClient restClient) {
        ProjectMember member = org.mockito.Mockito.mock(ProjectMember.class);
        ProjectIntegration integration = ProjectIntegration.builder()
                .id(30L)
                .linkType(LinkType.FIGMA)
                .credentialType(IntegrationCredentialType.OAUTH)
                .externalAccountId("figma-account")
                .externalAccountName("Figma")
                .providerConnectionId("figma-connection")
                .build();
        given(projectRepository.existsById(PROJECT_ID)).willReturn(true);
        given(projectAccessService.requireActiveMember(PROJECT_ID, USER_ID)).willReturn(member);
        given(integrationVerificationService.requireVerifiedConnection(PROJECT_ID, LinkType.FIGMA))
                .willReturn(integration);
        given(projectIntegrationService.decryptAccessToken(integration)).willReturn(TOKEN);
        given(integrationResourceRepository.findByProjectIntegrationIdAndProviderResourceId(30L, FILE_KEY))
                .willReturn(Optional.empty());
        given(integrationResourceRepository.saveAndFlush(any(IntegrationResource.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
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
