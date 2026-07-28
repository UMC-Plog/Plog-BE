package com.plog.domain.integration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.plog.domain.integration.dto.response.IntegrationCollectionResponse;
import com.plog.domain.integration.entity.IntegrationCredentialType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.IntegrationResourceStatus;
import com.plog.domain.integration.entity.IntegrationResourceType;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.IntegrationResourceRepository;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectAccessService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntegrationDataCollectionServiceTest {

    @Mock
    private IntegrationResourceRepository integrationResourceRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private ProjectIntegrationRepository projectIntegrationRepository;

    @Mock
    private IntegrationResourceService integrationResourceService;

    @Mock
    private IntegrationActivityStoreService integrationActivityStoreService;

    @Mock
    private IntegrationVerificationService integrationVerificationService;

    private IntegrationDataCollectionService integrationDataCollectionService;
    private IntegrationResourceCollector collector;

    @BeforeEach
    void setUp() {
        collector = new IntegrationResourceCollector() {
            @Override
            public LinkType provider() {
                return LinkType.GOOGLE;
            }

            @Override
            public void collect(IntegrationResource resource) {
                if ("missing-file".equals(resource.getProviderResourceId())) {
                    throw new ProviderResourceAccessException(404, null);
                }
            }
        };
        integrationDataCollectionService = new IntegrationDataCollectionService(
                integrationResourceRepository,
                projectRepository,
                projectAccessService,
                projectIntegrationRepository,
                integrationResourceService,
                List.of(collector),
                integrationActivityStoreService,
                integrationVerificationService
        );
    }

    @Test
    void collectsAvailableResourcesAndReturnsIdentifiablePartialFailures() {
        Long projectId = 1L;
        Long userId = 10L;
        Project project = Project.builder()
                .id(projectId)
                .projectName("Plog")
                .inviteTokenHash("hash")
                .inviteTokenEncrypted("encrypted")
                .projectType(ProjectType.DEVELOP)
                .status(ProjectStatus.IN_PROGRESS)
                .startDay(LocalDate.of(2026, 7, 1))
                .endDay(LocalDate.of(2026, 8, 1))
                .build();
        ProjectIntegration projectIntegration = ProjectIntegration.builder()
                .id(20L)
                .project(project)
                .linkType(LinkType.GOOGLE)
                .credentialType(IntegrationCredentialType.OAUTH)
                .externalAccountId("google-account")
                .externalAccountName("team@plog.test")
                .providerConnectionId("google-account")
                .build();
        IntegrationResource collectedResource = IntegrationResource.builder()
                .id(101L)
                .projectIntegration(projectIntegration)
                .resourceType(IntegrationResourceType.GOOGLE_DOCUMENT)
                .providerResourceId("available-file")
                .resourceName("프로젝트 기획서")
                .resourceUrl("https://docs.google.com/document/d/available-file")
                .resourceStatus(IntegrationResourceStatus.ACTIVE)
                .build();
        IntegrationResource missingResource = IntegrationResource.builder()
                .id(102L)
                .projectIntegration(projectIntegration)
                .resourceType(IntegrationResourceType.GOOGLE_PRESENTATION)
                .providerResourceId("missing-file")
                .resourceName("삭제된 발표자료")
                .resourceUrl("https://docs.google.com/presentation/d/missing-file")
                .resourceStatus(IntegrationResourceStatus.ACTIVE)
                .build();
        given(projectRepository.findByIdForUpdate(projectId)).willReturn(Optional.of(project));
        given(projectIntegrationRepository.findByProjectIdAndLinkType(projectId, LinkType.GITHUB))
                .willReturn(Optional.empty());
        given(integrationResourceRepository
                .findAllByProjectIntegrationProjectIdAndResourceStatusOrderByIdAsc(
                        projectId, IntegrationResourceStatus.ACTIVE))
                .willReturn(List.of(collectedResource, missingResource));

        IntegrationCollectionResponse response = integrationDataCollectionService.collectNow(projectId, userId);

        assertEquals(projectId, response.projectId());
        assertEquals(2, response.requestedResourceCount());
        assertEquals(1, response.collectedResourceCount(), response.toString());
        assertEquals(1, response.failures().size());
        assertEquals(102L, response.failures().get(0).resourceId());
        assertEquals(LinkType.GOOGLE, response.failures().get(0).linkType());
        assertEquals("삭제된 발표자료", response.failures().get(0).resourceName());
        assertEquals("provider resource not found", response.failures().get(0).reason());
        verify(projectAccessService).requireActiveMember(projectId, userId);
        verify(integrationVerificationService).requireVerifiedConnection(projectId, LinkType.GOOGLE);
        assertEquals(IntegrationResourceStatus.ACTIVE, collectedResource.getResourceStatus());
        assertNotNull(collectedResource.getLastCollectedAt());
        assertEquals(IntegrationResourceStatus.DISABLED, missingResource.getResourceStatus());
        verify(integrationActivityStoreService, times(2)).beginResourceCollection();
        verify(integrationActivityStoreService, times(2)).endResourceCollection();
    }
}
