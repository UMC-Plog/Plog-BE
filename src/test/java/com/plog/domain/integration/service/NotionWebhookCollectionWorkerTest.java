package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.integration.config.NotionWebhookProperties;
import com.plog.domain.integration.entity.IntegrationConnectionStatus;
import com.plog.domain.integration.entity.IntegrationCredentialType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.IntegrationResourceStatus;
import com.plog.domain.integration.entity.IntegrationResourceType;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.IntegrationResourceRepository;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotionWebhookCollectionWorkerTest {

    @Mock
    private NotionWebhookQueueService queueService;
    @Mock
    private ProjectIntegrationRepository projectIntegrationRepository;
    @Mock
    private IntegrationResourceRepository integrationResourceRepository;
    @Mock
    private NotionIntegrationResourceCollector notionCollector;
    @Mock
    private IntegrationActivityStoreService activityStoreService;
    @Mock
    private IntegrationResourceCollectionStateService resourceStateService;
    @Mock
    private ProjectIntegrationService projectIntegrationService;

    private NotionWebhookCollectionWorker worker;
    private ProjectIntegration integration;
    private IntegrationResource resource;
    private NotionWebhookBatch batch;

    @BeforeEach
    void setUp() {
        NotionWebhookProperties properties = new NotionWebhookProperties(
                "verification-token", Duration.ofMinutes(3), Duration.ofMinutes(30),
                5, 10_000, 10);
        worker = new NotionWebhookCollectionWorker(
                queueService,
                properties,
                projectIntegrationRepository,
                integrationResourceRepository,
                notionCollector,
                activityStoreService,
                resourceStateService,
                projectIntegrationService,
                new ObjectMapper()
        );
        integration = ProjectIntegration.builder()
                .id(10L)
                .linkType(LinkType.NOTION)
                .credentialType(IntegrationCredentialType.OAUTH)
                .externalAccountId("workspace-1")
                .externalAccountName("Plog Workspace")
                .providerConnectionId("bot-1")
                .connectionStatus(IntegrationConnectionStatus.ACTIVE)
                .build();
        resource = IntegrationResource.builder()
                .id(20L)
                .projectIntegration(integration)
                .resourceType(IntegrationResourceType.NOTION_PAGE)
                .providerResourceId("root-page")
                .resourceName("회의록")
                .resourceUrl("https://notion.so/root-page")
                .resourceStatus(IntegrationResourceStatus.ACTIVE)
                .build();
        batch = new NotionWebhookBatch(
                List.of(new NotionWebhookBatch.Event(
                        1L,
                        "event-1",
                        "workspace-1",
                        "page.content_updated",
                        "child-page",
                        "page",
                        "root-page",
                        "page",
                        "[{\"id\":\"actor-1\",\"type\":\"person\"}]",
                        Instant.parse("2026-08-02T10:00:00Z"),
                        "{\"id\":\"event-1\"}"
                )),
                "claim-token",
                1
        );
        given(queueService.claimNext(any()))
                .willReturn(batch)
                .willReturn((NotionWebhookBatch) null);
        given(projectIntegrationRepository.findAllByLinkTypeAndExternalAccountIdAndConnectionStatus(
                LinkType.NOTION, "workspace-1", IntegrationConnectionStatus.ACTIVE))
                .willReturn(List.of(integration));
        given(integrationResourceRepository
                .findAllByProjectIntegrationIdAndResourceStatusOrderByIdAsc(
                        10L, IntegrationResourceStatus.ACTIVE))
                .willReturn(List.of(resource));
    }

    @Test
    void storesWebhookActorAndCollectsChangedEntityWithinRegisteredRoot() {
        given(notionCollector.findContainingResource(any(), any(), any())).willReturn(resource);

        worker.processDueEvents();

        verify(activityStoreService).store(
                eq(resource),
                eq(com.plog.domain.integration.entity.IntegrationActivityType.NOTION_WEBHOOK_EVENT),
                eq("webhook:event-1:actor-1"),
                eq("actor-1"),
                any(),
                any(),
                eq(Instant.parse("2026-08-02T10:00:00Z")),
                eq("https://notion.so/root-page"),
                eq("{\"id\":\"event-1\"}")
        );
        ArgumentCaptor<CollectionContext> contextCaptor = ArgumentCaptor.forClass(CollectionContext.class);
        verify(notionCollector).findContainingResource(any(), any(), contextCaptor.capture());
        verify(notionCollector).collectChangedEntity(eq(resource), any(), contextCaptor.capture());
        assertThat(contextCaptor.getAllValues())
                .allSatisfy(context -> assertThat(context.cursor()).isEqualTo(CollectionCursor.start()));
        verify(resourceStateService).markCollected(eq(20L), any());
        verify(queueService).succeed(eq(batch), any());
    }

    @Test
    void unauthorizedProviderResponseRequiresProjectReauthorization() {
        given(notionCollector.findContainingResource(any(), any(), any())).willReturn(resource);
        org.mockito.Mockito.doThrow(new ProviderResourceAccessException(401, null))
                .when(notionCollector).collectChangedEntity(eq(resource), any(), any());

        worker.processDueEvents();

        verify(projectIntegrationService).requireReauthorization(10L);
        verify(queueService).requireReauthorization(eq(batch), any(), anyString());
        verify(queueService, never()).succeed(any(), any());
    }

    @Test
    void temporaryProviderFailureSchedulesBoundedRetry() {
        given(notionCollector.findContainingResource(any(), any(), any())).willReturn(resource);
        org.mockito.Mockito.doThrow(new ProviderResourceAccessException(429, null))
                .when(notionCollector).collectChangedEntity(eq(resource), any(), any());

        worker.processDueEvents();

        verify(resourceStateService).markRetrying(eq(20L), any(), anyString());
        verify(queueService).retry(eq(batch), any(), any(), anyString());
        verify(queueService, never()).fail(any(), any(), anyString());
    }

    @Test
    void temporaryFailureDuringResourceLookupFailsAfterRetryLimit() {
        NotionWebhookBatch exhaustedBatch = new NotionWebhookBatch(
                batch.events(), batch.claimToken(), 5);
        given(queueService.claimNext(any()))
                .willReturn(exhaustedBatch)
                .willReturn((NotionWebhookBatch) null);
        given(notionCollector.findContainingResource(any(), any(), any()))
                .willThrow(new ProviderResourceAccessException(503, null));

        worker.processDueEvents();

        verify(queueService).fail(eq(exhaustedBatch), any(), anyString());
        verify(queueService, never()).ignore(any(), any(), anyString());
        verify(queueService, never()).retry(any(), any(), any(), anyString());
    }

    @Test
    void ignoresEventOutsideEveryRegisteredResource() {
        given(notionCollector.findContainingResource(any(), any(), any())).willReturn(null);

        worker.processDueEvents();

        verify(queueService).ignore(eq(batch), any(), eq("event is outside registered project resources"));
        verify(notionCollector, never()).collectChangedEntity(any(), any(), any());
    }
}
