package com.plog.domain.integration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.plog.domain.integration.entity.IntegrationCredentialType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.IntegrationResourceStatus;
import com.plog.domain.integration.entity.IntegrationResourceType;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.IntegrationResourceRepository;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientResponseException;

@ExtendWith(MockitoExtension.class)
class IntegrationDataCollectionServiceTest {

    @Mock
    private IntegrationResourceRepository integrationResourceRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private IntegrationResourceService integrationResourceService;
    @Mock
    private IntegrationActivityStoreService integrationActivityStoreService;
    @Mock
    private IntegrationVerificationService integrationVerificationService;
    @Mock
    private IntegrationResourceCollectionStateService resourceCollectionStateService;
    @Mock
    private ProjectIntegrationService projectIntegrationService;
    @Mock
    private IntegrationCollectionJobService integrationCollectionJobService;

    private IntegrationDataCollectionService integrationDataCollectionService;
    private IntegrationResourceCollector collector;

    @BeforeEach
    void setUp() {
        // Docs/Slides 둘 다 처리하는 구글 컬렉터 하나로 가정
        collector = new IntegrationResourceCollector() {
            @Override
            public List<LinkType> providers() {
                return List.of(LinkType.GOOGLE_DOCS, LinkType.GOOGLE_SLIDES);
            }

            @Override
            public void collect(
                    IntegrationResource resource,
                    ProjectIntegration verifiedIntegration,
                    CollectionContext context
            ) {
                if ("missing-file".equals(resource.getProviderResourceId())) {
                    throw new ProviderResourceAccessException(404, null);
                }
            }
        };
        integrationDataCollectionService = new IntegrationDataCollectionService(
                integrationResourceRepository,
                projectRepository,
                projectAccessService,
                integrationResourceService,
                List.of(collector),
                integrationActivityStoreService,
                integrationVerificationService,
                resourceCollectionStateService,
                projectIntegrationService,
                integrationCollectionJobService
        );
    }

    @Test
    void collectsAvailableResourcesAndReturnsIdentifiablePartialFailures() {
        Long projectId = 1L;
        Project project = project(projectId);

        // Docs 연동 / Slides 연동을 각각 별개의 ProjectIntegration으로 분리
        ProjectIntegration googleDocsIntegration = googleIntegration(project, LinkType.GOOGLE_DOCS, 20L);
        ProjectIntegration googleSlidesIntegration = googleIntegration(project, LinkType.GOOGLE_SLIDES, 21L);

        IntegrationResource collectedResource = IntegrationResource.builder()
                .id(101L)
                .projectIntegration(googleDocsIntegration)
                .resourceType(IntegrationResourceType.GOOGLE_DOCUMENT)
                .providerResourceId("available-file")
                .resourceName("프로젝트 기획서")
                .resourceUrl("https://docs.google.com/document/d/available-file")
                .resourceStatus(IntegrationResourceStatus.ACTIVE)
                .build();
        IntegrationResource missingResource = IntegrationResource.builder()
                .id(102L)
                .projectIntegration(googleSlidesIntegration)
                .resourceType(IntegrationResourceType.GOOGLE_PRESENTATION)
                .providerResourceId("missing-file")
                .resourceName("삭제된 발표자료")
                .resourceUrl("https://docs.google.com/presentation/d/missing-file")
                .resourceStatus(IntegrationResourceStatus.ACTIVE)
                .build();

        given(integrationResourceRepository
                .findAllByProjectIntegrationProjectIdAndResourceStatusOrderByIdAsc(
                        projectId, IntegrationResourceStatus.ACTIVE))
                .willReturn(List.of(collectedResource, missingResource));

        IntegrationDataCollectionService.CollectionOutcome response =
                integrationDataCollectionService.runCollection(projectId, CollectionContext.noop());

        assertEquals(2, response.requestedResourceCount());
        assertEquals(1, response.collectedResourceCount());
        assertEquals(1, response.failures().size());
        assertEquals(102L, response.failures().get(0).resourceId());
        assertEquals("삭제된 발표자료", response.failures().get(0).resourceName());
        assertEquals("provider resource not found", response.failures().get(0).reason());

        // 연동이 두 개(Docs/Slides)라서 각각 검증됨
        verify(integrationVerificationService).requireVerifiedConnection(projectId, LinkType.GOOGLE_DOCS);
        verify(integrationVerificationService).requireVerifiedConnection(projectId, LinkType.GOOGLE_SLIDES);
        verify(resourceCollectionStateService).markCollected(
                org.mockito.ArgumentMatchers.eq(collectedResource.getId()),
                org.mockito.ArgumentMatchers.any()
        );
        verify(resourceCollectionStateService).disable(
                org.mockito.ArgumentMatchers.eq(missingResource.getId()),
                org.mockito.ArgumentMatchers.any()
        );
        verify(integrationActivityStoreService, times(2)).beginResourceCollection();
        verify(integrationActivityStoreService, times(2)).endResourceCollection();
    }

    @Test
    void collectsWithIntegrationReturnedByVerification() {
        Long projectId = 1L;
        Project project = project(projectId);
        ProjectIntegration staleIntegration = ProjectIntegration.builder()
                .id(20L)
                .project(project)
                .linkType(LinkType.GOOGLE_DOCS)
                .credentialType(IntegrationCredentialType.OAUTH)
                .externalAccountId("google-account")
                .externalAccountName("team@plog.test")
                .providerConnectionId("google-account")
                .accessTokenEncrypted("stale-token")
                .build();
        ProjectIntegration refreshedIntegration = ProjectIntegration.builder()
                .id(20L)
                .project(project)
                .linkType(LinkType.GOOGLE_DOCS)
                .credentialType(IntegrationCredentialType.OAUTH)
                .externalAccountId("google-account")
                .externalAccountName("team@plog.test")
                .providerConnectionId("google-account")
                .accessTokenEncrypted("refreshed-token")
                .build();
        IntegrationResource resource = IntegrationResource.builder()
                .id(101L)
                .projectIntegration(staleIntegration)
                .resourceType(IntegrationResourceType.GOOGLE_DOCUMENT)
                .providerResourceId("available-file")
                .resourceName("프로젝트 기획서")
                .resourceUrl("https://docs.google.com/document/d/available-file")
                .resourceStatus(IntegrationResourceStatus.ACTIVE)
                .build();
        AtomicReference<ProjectIntegration> collectedIntegration = new AtomicReference<>();
        IntegrationResourceCollector recordingCollector = new IntegrationResourceCollector() {
            @Override
            public List<LinkType> providers() {
                return List.of(LinkType.GOOGLE_DOCS);
            }

            @Override
            public void collect(
                    IntegrationResource target,
                    ProjectIntegration verifiedIntegration,
                    CollectionContext context
            ) {
                collectedIntegration.set(verifiedIntegration);
            }
        };
        IntegrationDataCollectionService service = serviceWith(recordingCollector);
        given(integrationResourceRepository
                .findAllByProjectIntegrationProjectIdAndResourceStatusOrderByIdAsc(
                        projectId, IntegrationResourceStatus.ACTIVE))
                .willReturn(List.of(resource));
        given(integrationVerificationService.requireVerifiedConnection(projectId, LinkType.GOOGLE_DOCS))
                .willReturn(refreshedIntegration);

        service.runCollection(projectId, CollectionContext.noop());

        assertSame(refreshedIntegration, collectedIntegration.get());
    }

    @Test
    void enqueueCollectionQueuesJobWithoutCollecting() {
        Long projectId = 1L;
        Long userId = 10L;
        ProjectMember member = ProjectMember.builder().id(100L).build();
        given(projectRepository.existsById(projectId)).willReturn(true);
        given(projectAccessService.requireActiveMember(projectId, userId)).willReturn(member);

        integrationDataCollectionService.enqueueCollection(projectId, userId);

        verify(integrationCollectionJobService).enqueue(projectId, 100L);
        verify(integrationResourceRepository, never())
                .findAllByProjectIntegrationProjectIdAndResourceStatusOrderByIdAsc(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void requiresReauthorizationWhenProviderReturnsUnauthorized() {
        assertReauthorizationFailure(401, "provider credential revoked");
    }

    @Test
    void requiresReauthorizationWhenProviderReturnsForbidden() {
        assertReauthorizationFailure(403, "provider resource access denied");
    }

    @Test
    void retriesTemporaryFailureTwiceAndReturnsUnavailableFailure() {
        Long projectId = 1L;
        IntegrationResource resource = resource(project(projectId), LinkType.GOOGLE_DOCS, "temporary-file");
        CountingFailureCollector failureCollector = new CountingFailureCollector(503, LinkType.GOOGLE_DOCS);
        IntegrationDataCollectionService service = serviceWith(failureCollector);
        given(integrationResourceRepository
                .findAllByProjectIntegrationProjectIdAndResourceStatusOrderByIdAsc(
                        projectId, IntegrationResourceStatus.ACTIVE))
                .willReturn(List.of(resource));

        IntegrationDataCollectionService.CollectionOutcome response =
                service.runCollection(projectId, CollectionContext.noop());

        assertEquals(0, response.collectedResourceCount());
        assertEquals("provider temporarily unavailable", response.failures().get(0).reason());
        assertEquals(2, failureCollector.attempts());
        verify(integrationActivityStoreService, times(2)).beginResourceCollection();
        verify(integrationActivityStoreService, times(2)).endResourceCollection();
    }

    @Test
    void rateLimitedForbiddenRequeuesJobInsteadOfFailingResource() {
        Long projectId = 1L;
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-ratelimit-remaining", "0");
        headers.add("x-ratelimit-reset", String.valueOf(Instant.now().plusSeconds(900).getEpochSecond()));
        IntegrationResource resource = resource(project(projectId), LinkType.GITHUB, "repo");
        IntegrationDataCollectionService service = serviceWith(new RateLimitedCollector(headers));
        given(integrationResourceRepository
                .findAllByProjectIntegrationProjectIdAndResourceStatusOrderByIdAsc(
                        projectId, IntegrationResourceStatus.ACTIVE))
                .willReturn(List.of(resource));

        // rate limit 창은 한 시간이라 인라인 재시도로는 열리지 않는다. 잡 레벨로 올려야 한다.
        assertThrows(CollectionRetryableException.class,
                () -> service.runCollection(projectId, CollectionContext.noop()));

        verify(projectIntegrationService, never()).requireReauthorization(anyLong());
    }

    @Test
    void repositorySynchronizationOutageRequeuesJobInsteadOfFailingIt() {
        Long projectId = 1L;
        willThrow(new ApiException(IntegrationErrorCode.PROVIDER_TEMPORARILY_UNAVAILABLE))
                .given(integrationResourceService).registerGithubInstallationRepositories(projectId);

        assertThrows(CollectionRetryableException.class,
                () -> integrationDataCollectionService.runCollection(projectId, CollectionContext.noop()));
    }

    @Test
    void requiresReauthorizationWhenForbiddenIsNotRateLimit() {
        Long projectId = 1L;
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-ratelimit-remaining", "4999");
        IntegrationResource resource = resource(project(projectId), LinkType.GITHUB, "repo");
        IntegrationDataCollectionService service = serviceWith(new RateLimitedCollector(headers));
        given(integrationResourceRepository
                .findAllByProjectIntegrationProjectIdAndResourceStatusOrderByIdAsc(
                        projectId, IntegrationResourceStatus.ACTIVE))
                .willReturn(List.of(resource));

        IntegrationDataCollectionService.CollectionOutcome response =
                service.runCollection(projectId, CollectionContext.noop());

        assertEquals("provider resource access denied", response.failures().get(0).reason());
        verify(projectIntegrationService).requireReauthorization(anyLong());
    }

    private void assertReauthorizationFailure(int statusCode, String expectedReason) {
        Long projectId = 1L;
        IntegrationResource resource = resource(project(projectId), LinkType.GOOGLE_DOCS, "denied-file");
        IntegrationDataCollectionService service =
                serviceWith(new CountingFailureCollector(statusCode, LinkType.GOOGLE_DOCS));
        given(integrationResourceRepository
                .findAllByProjectIntegrationProjectIdAndResourceStatusOrderByIdAsc(
                        projectId, IntegrationResourceStatus.ACTIVE))
                .willReturn(List.of(resource));

        IntegrationDataCollectionService.CollectionOutcome response =
                service.runCollection(projectId, CollectionContext.noop());

        assertEquals(0, response.collectedResourceCount());
        assertEquals(expectedReason, response.failures().get(0).reason());
        verify(projectIntegrationService).requireReauthorization(resource.getProjectIntegration().getId());
    }

    private IntegrationDataCollectionService serviceWith(IntegrationResourceCollector resourceCollector) {
        return new IntegrationDataCollectionService(
                integrationResourceRepository,
                projectRepository,
                projectAccessService,
                integrationResourceService,
                List.of(resourceCollector),
                integrationActivityStoreService,
                integrationVerificationService,
                resourceCollectionStateService,
                projectIntegrationService,
                integrationCollectionJobService
        );
    }

    private Project project(Long projectId) {
        return Project.builder()
                .id(projectId)
                .projectName("Plog")
                .inviteTokenHash("hash")
                .inviteTokenEncrypted("encrypted")
                .projectType(ProjectType.DEVELOP)
                .status(ProjectStatus.IN_PROGRESS)
                .startDay(LocalDate.of(2026, 7, 1))
                .endDay(LocalDate.of(2026, 8, 1))
                .build();
    }

    private ProjectIntegration googleIntegration(Project project, LinkType linkType, Long id) {
        return ProjectIntegration.builder()
                .id(id)
                .project(project)
                .linkType(linkType)
                .credentialType(IntegrationCredentialType.OAUTH)
                .externalAccountId("google-account")
                .externalAccountName("team@plog.test")
                .providerConnectionId("google-account")
                .build();
    }

    private IntegrationResource resource(Project project, LinkType linkType, String providerResourceId) {
        ProjectIntegration integration = googleIntegration(project, linkType, 20L);
        return IntegrationResource.builder()
                .id(101L)
                .projectIntegration(integration)
                .resourceType(IntegrationResourceType.GOOGLE_DOCUMENT)
                .providerResourceId(providerResourceId)
                .resourceName("프로젝트 기획서")
                .resourceUrl("https://docs.google.com/document/d/" + providerResourceId)
                .resourceStatus(IntegrationResourceStatus.ACTIVE)
                .build();
    }

    private static final class CountingFailureCollector implements IntegrationResourceCollector {
        private final int statusCode;
        private final LinkType linkType;
        private int attempts;

        private CountingFailureCollector(int statusCode, LinkType linkType) {
            this.statusCode = statusCode;
            this.linkType = linkType;
        }

        @Override
        public List<LinkType> providers() {
            return List.of(linkType);
        }

        @Override
        public void collect(
                IntegrationResource resource,
                ProjectIntegration verifiedIntegration,
                CollectionContext context
        ) {
            attempts++;
            throw new ProviderResourceAccessException(statusCode, null);
        }

        private int attempts() {
            return attempts;
        }
    }

    /** rate limit 헤더를 실은 403을 던져 재인증 분기와 일시 실패 분기를 가른다. */
    private static final class RateLimitedCollector implements IntegrationResourceCollector {
        private final HttpHeaders headers;

        private RateLimitedCollector(HttpHeaders headers) {
            this.headers = headers;
        }

        @Override
        public List<LinkType> providers() {
            return List.of(LinkType.GITHUB);
        }

        @Override
        public void collect(
                IntegrationResource resource,
                ProjectIntegration verifiedIntegration,
                CollectionContext context
        ) {
            throw new ProviderResourceAccessException(403, new RestClientResponseException(
                    "forbidden", 403, "Forbidden", headers,
                    "{}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        }
    }
}
