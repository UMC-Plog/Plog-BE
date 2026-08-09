package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.plog.domain.integration.dto.response.IntegrationResourceRemovalResponse;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.IntegrationActivityRepository;
import com.plog.domain.integration.repository.IntegrationResourceRepository;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.report.service.IntegrationActivityReportLogAdapter;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class IntegrationResourceServiceRemovalTest {

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
    void removingResourceAlsoDeletesItsDerivedReportProjection() {
        ProjectIntegration integration = ProjectIntegration.builder()
                .id(5L)
                .linkType(LinkType.FIGMA)
                .providerConnectionId("figma-account")
                .build();
        IntegrationResource resource = IntegrationResource.builder()
                .id(10L)
                .projectIntegration(integration)
                .providerResourceId("figma-file-1")
                .build();
        given(projectRepository.existsById(40L)).willReturn(true);
        given(projectAccessService.requireActiveMember(40L, 63L))
                .willReturn(mock(ProjectMember.class));
        given(projectIntegrationRepository.findByProjectIdAndLinkType(40L, LinkType.FIGMA))
                .willReturn(Optional.of(integration));
        given(integrationResourceRepository.findByIdAndProjectIntegrationIdForUpdate(10L, 5L))
                .willReturn(Optional.of(resource));

        IntegrationResourceRemovalResponse response = service().removeResource(
                40L, 63L, LinkType.FIGMA, 10L);

        assertThat(response).isEqualTo(new IntegrationResourceRemovalResponse(40L, LinkType.FIGMA, 10L));
        verify(reportLogAdapter).deleteResourceProjection(40L, LinkType.FIGMA, "figma-file-1");
        verify(integrationActivityRepository).deleteAllByIntegrationResourceId(10L);
        verify(integrationResourceRepository).delete(resource);
    }

    private IntegrationResourceService service() {
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
                RestClient.create()
        );
    }
}
