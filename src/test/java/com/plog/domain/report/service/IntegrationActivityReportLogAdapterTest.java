package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.integration.entity.IntegrationActivity;
import com.plog.domain.integration.entity.IntegrationActivityType;
import com.plog.domain.integration.entity.IntegrationCredentialType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.IntegrationResourceStatus;
import com.plog.domain.integration.entity.IntegrationResourceType;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.IntegrationActivityRepository;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntegrationActivityReportLogAdapterTest {

    @Mock
    private IntegrationActivityRepository integrationActivityRepository;
    @Mock
    private ReportActivityLogRepository reportActivityLogRepository;

    private IntegrationActivityReportLogAdapter adapter;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        adapter = new IntegrationActivityReportLogAdapter(
                integrationActivityRepository,
                reportActivityLogRepository,
                new ExternalActivityCompetencyMapper(objectMapper),
                objectMapper
        );
    }

    @Test
    void synchronizeActivityProjectsMappedActivityOnly() {
        when(integrationActivityRepository.findReportProjectionTarget(10L, "commit:abc123"))
                .thenReturn(Optional.of(activity(
                        LinkType.GITHUB,
                        IntegrationActivityType.GITHUB_COMMIT,
                        ProjectMember.builder().id(63L).build(),
                        "commit:abc123",
                        "wantkdd",
                        "wantkdd@example.com",
                        "{\"sha\":\"abc123\"}"
                )));

        adapter.synchronizeActivity(10L, "commit:abc123");

        ArgumentCaptor<String> sourceRefId = ArgumentCaptor.forClass(String.class);
        verify(reportActivityLogRepository).upsertExternalActivityLog(
                eq(63L),
                eq("GITHUB"),
                eq("GITHUB_COMMIT"),
                eq(null),
                eq(LocalDateTime.of(2026, 8, 1, 12, 4, 5)),
                eq("{\"sha\":\"abc123\"}"),
                sourceRefId.capture()
        );
        assertThat(sourceRefId.getValue())
                .startsWith("integration:40:GITHUB:")
                .doesNotContain("commit:abc123");
    }

    @Test
    void synchronizeActivityUsesSameSourceRefIdForRepeatedActivity() {
        when(integrationActivityRepository.findReportProjectionTarget(10L, "commit:abc123"))
                .thenReturn(Optional.of(activity(
                        LinkType.GITHUB,
                        IntegrationActivityType.GITHUB_COMMIT,
                        ProjectMember.builder().id(63L).build(),
                        "commit:abc123",
                        "wantkdd",
                        "wantkdd@example.com",
                        "{\"sha\":\"abc123\"}"
                )));

        adapter.synchronizeActivity(10L, "commit:abc123");
        adapter.synchronizeActivity(10L, "commit:abc123");

        ArgumentCaptor<String> sourceRefId = ArgumentCaptor.forClass(String.class);
        verify(reportActivityLogRepository, times(2)).upsertExternalActivityLog(
                eq(63L),
                eq("GITHUB"),
                eq("GITHUB_COMMIT"),
                eq(null),
                eq(LocalDateTime.of(2026, 8, 1, 12, 4, 5)),
                eq("{\"sha\":\"abc123\"}"),
                sourceRefId.capture()
        );
        assertThat(sourceRefId.getAllValues())
                .hasSize(2)
                .containsOnly(sourceRefId.getAllValues().get(0));
    }

    @Test
    void synchronizeActivityDeletesStaleProjectionWhenActivityIsUnmapped() {
        when(integrationActivityRepository.findReportProjectionTarget(10L, "comment:comment-1"))
                .thenReturn(Optional.of(activity(
                        LinkType.GOOGLE_DOCS,
                        IntegrationActivityType.GOOGLE_DRIVE_COMMENT,
                        null,
                        "comment:comment-1",
                        "유상완",
                        "sangwan@example.com",
                        "{\"id\":\"comment-1\"}"
                )));

        adapter.synchronizeActivity(10L, "comment:comment-1");

        verify(reportActivityLogRepository).deleteExternalActivityLog(eq("GOOGLE"), any());
        verify(reportActivityLogRepository, never()).upsertExternalActivityLog(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void deletedGoogleCommentDeletesStaleProjectionThroughCompetencyRule() {
        when(integrationActivityRepository.findReportProjectionTarget(10L, "comment:deleted"))
                .thenReturn(Optional.of(activity(
                        LinkType.GOOGLE_SLIDES,
                        IntegrationActivityType.GOOGLE_DRIVE_COMMENT,
                        ProjectMember.builder().id(63L).build(),
                        "comment:deleted",
                        "유상완",
                        "sangwan@example.com",
                        "{\"id\":\"comment-1\",\"deleted\":true}"
                )));

        adapter.synchronizeActivity(10L, "comment:deleted");

        verify(reportActivityLogRepository).deleteExternalActivityLog(eq("GOOGLE"), any());
        verify(reportActivityLogRepository, never()).upsertExternalActivityLog(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void mappedNotionPageBlockAndDataSourceSnapshotsAreProjectedForEvidence() {
        when(integrationActivityRepository.findReportProjectionTarget(10L, "data-source:data-source-1:edited"))
                .thenReturn(Optional.of(activity(
                        LinkType.NOTION,
                        IntegrationActivityType.NOTION_DATA_SOURCE_SNAPSHOT,
                        ProjectMember.builder().id(63L).build(),
                        "data-source:data-source-1:edited",
                        "유상완",
                        "sangwan@example.com",
                        "{\"id\":\"data-source-1\",\"title\":[{\"plain_text\":\"요구사항 DB\"}]}"
                )));
        when(integrationActivityRepository.findReportProjectionTarget(10L, "page:page-1:edited"))
                .thenReturn(Optional.of(activity(
                        LinkType.NOTION,
                        IntegrationActivityType.NOTION_PAGE_SNAPSHOT,
                        ProjectMember.builder().id(63L).build(),
                        "page:page-1:edited",
                        "유상완",
                        "sangwan@example.com",
                        "{\"id\":\"page-1\",\"properties\":{\"이름\":{\"type\":\"title\",\"title\":[{\"plain_text\":\"API 명세\"}]}}}"
                )));
        when(integrationActivityRepository.findReportProjectionTarget(10L, "block:block-1:edited"))
                .thenReturn(Optional.of(activity(
                        LinkType.NOTION,
                        IntegrationActivityType.NOTION_BLOCK_SNAPSHOT,
                        ProjectMember.builder().id(63L).build(),
                        "block:block-1:edited",
                        "유상완",
                        "sangwan@example.com",
                        "{\"id\":\"block-1\",\"type\":\"paragraph\",\"paragraph\":{\"rich_text\":[{\"plain_text\":\"배포 체크\"}]}}"
                )));

        adapter.synchronizeActivity(10L, "data-source:data-source-1:edited");
        adapter.synchronizeActivity(10L, "page:page-1:edited");
        adapter.synchronizeActivity(10L, "block:block-1:edited");

        ArgumentCaptor<String> rawType = ArgumentCaptor.forClass(String.class);
        verify(reportActivityLogRepository, times(3)).upsertExternalActivityLog(
                eq(63L),
                eq("NOTION"),
                rawType.capture(),
                eq(null),
                eq(LocalDateTime.of(2026, 8, 1, 12, 4, 5)),
                any(),
                any()
        );
        assertThat(rawType.getAllValues())
                .containsExactly("NOTION_DATA_SOURCE_SNAPSHOT", "NOTION_PAGE_SNAPSHOT", "NOTION_BLOCK_SNAPSHOT");
        verify(reportActivityLogRepository, never()).deleteExternalActivityLog(eq("NOTION"), any());
    }

    @Test
    void notionWebhookEventIsNotProjectedAsReportActivityLog() {
        when(integrationActivityRepository.findReportProjectionTarget(10L, "webhook:event-1"))
                .thenReturn(Optional.of(activity(
                        LinkType.NOTION,
                        IntegrationActivityType.NOTION_WEBHOOK_EVENT,
                        ProjectMember.builder().id(63L).build(),
                        "webhook:event-1",
                        "유상완",
                        "sangwan@example.com",
                        "{\"entity\":{\"id\":\"page-1\"}}"
                )));

        adapter.synchronizeActivity(10L, "webhook:event-1");

        verify(reportActivityLogRepository).deleteExternalActivityLog(eq("NOTION"), any());
        verify(reportActivityLogRepository, never()).upsertExternalActivityLog(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void unsupportedSnapshotAndGithubBotDeleteStaleProjection() {
        when(integrationActivityRepository.findReportProjectionTarget(10L, "metadata:file"))
                .thenReturn(Optional.of(activity(
                        LinkType.FIGMA,
                        IntegrationActivityType.FIGMA_FILE_METADATA,
                        ProjectMember.builder().id(63L).build(),
                        "metadata:file",
                        null,
                        null,
                        "{}"
                )));
        adapter.synchronizeActivity(10L, "metadata:file");

        when(integrationActivityRepository.findReportProjectionTarget(10L, "commit:bot"))
                .thenReturn(Optional.of(activity(
                        LinkType.GITHUB,
                        IntegrationActivityType.GITHUB_COMMIT,
                        ProjectMember.builder().id(63L).build(),
                        "commit:bot",
                        "dependabot[bot]",
                        null,
                        "{}"
                )));
        adapter.synchronizeActivity(10L, "commit:bot");

        verify(reportActivityLogRepository).deleteExternalActivityLog(eq("FIGMA"), any());
        verify(reportActivityLogRepository).deleteExternalActivityLog(eq("GITHUB"), any());
        verify(reportActivityLogRepository, never()).upsertExternalActivityLog(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void outsideProjectPeriodDeletesStaleProjection() {
        IntegrationActivity activity = IntegrationActivity.builder()
                .id(30L)
                .integrationResource(resource(LinkType.NOTION))
                .projectMember(ProjectMember.builder().id(63L).build())
                .activityType(IntegrationActivityType.NOTION_COMMENT)
                .providerEventKey("comment:notion-comment-1")
                .actorLogin("유상완")
                .actorEmail("sangwan@example.com")
                .occurredAt(Instant.parse("2026-09-01T03:04:05Z"))
                .providerPayload("{\"id\":\"notion-comment-1\"}")
                .build();
        when(integrationActivityRepository.findReportProjectionTarget(10L, "comment:notion-comment-1"))
                .thenReturn(Optional.of(activity));

        adapter.synchronizeActivity(10L, "comment:notion-comment-1");

        verify(reportActivityLogRepository).deleteExternalActivityLog(eq("NOTION"), any());
        verify(reportActivityLogRepository, never()).upsertExternalActivityLog(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void googleDocsAndSlidesUseDifferentProjectionPrefixes() {
        adapter.deleteProjectProjection(40L, LinkType.GOOGLE_DOCS);
        adapter.deleteProjectProjection(40L, LinkType.GOOGLE_SLIDES);

        ArgumentCaptor<String> prefix = ArgumentCaptor.forClass(String.class);
        verify(reportActivityLogRepository, times(2))
                .deleteExternalActivityLogsBySourcePrefix(eq("GOOGLE"), prefix.capture());
        assertThat(prefix.getAllValues())
                .containsExactly("integration:40:GOOGLE_DOCS:", "integration:40:GOOGLE_SLIDES:");
    }

    @Test
    void endDayChangeSynchronizesOnlyChangedActivityWindow() {
        IntegrationActivity activity = activity(
                LinkType.GITHUB,
                IntegrationActivityType.GITHUB_COMMIT,
                ProjectMember.builder().id(63L).build(),
                "commit:abc123",
                "wantkdd",
                "wantkdd@example.com",
                "{\"sha\":\"abc123\"}"
        );
        when(integrationActivityRepository.findReportProjectionTargetsByProjectAndActivityWindow(
                eq(40L),
                // 저장 기준이 KST라 같은 경계를 절대시각으로 바꾸면 -9h 가 된다.
                eq(Instant.parse("2026-08-01T15:00:00Z")),
                eq(Instant.parse("2026-08-03T15:00:00Z")),
                eq(LocalDateTime.of(2026, 8, 2, 0, 0)),
                eq(LocalDateTime.of(2026, 8, 4, 0, 0))
        )).thenReturn(List.of(activity));

        adapter.synchronizeProjectActivitiesForEndDayChange(
                40L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));

        verify(reportActivityLogRepository).upsertExternalActivityLog(
                eq(63L),
                eq("GITHUB"),
                eq("GITHUB_COMMIT"),
                eq(null),
                eq(LocalDateTime.of(2026, 8, 1, 12, 4, 5)),
                eq("{\"sha\":\"abc123\"}"),
                any()
        );
        verify(integrationActivityRepository, never()).findReportProjectionTargetsByProjectIntegration(any());
    }

    @Test
    void blankSynchronizationRequestsDoNothing() {
        adapter.synchronizeActivity(null, "commit:abc123");
        adapter.synchronizeActivity(10L, " ");
        adapter.synchronizeProjectMemberActivities(null, 63L);
        adapter.synchronizeProjectMemberActivities(5L, null);
        adapter.synchronizeProviderActorActivities(null, "actor", null, null);
        adapter.synchronizeProviderActorActivities(5L, " ", null, null);
        adapter.synchronizeProjectIntegrationActivities(null);
        adapter.synchronizeProjectActivitiesForEndDayChange(null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2));
        adapter.synchronizeProjectActivitiesForEndDayChange(40L, null, LocalDate.of(2026, 8, 2));
        adapter.synchronizeProjectActivitiesForEndDayChange(40L, LocalDate.of(2026, 8, 1), null);
        adapter.synchronizeProjectActivitiesForEndDayChange(
                40L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1));
        adapter.deleteProjectMemberProjection(null, LinkType.GITHUB, 63L);
        adapter.deleteProjectMemberProjection(40L, null, 63L);
        adapter.deleteProjectMemberProjection(40L, LinkType.GITHUB, null);
        adapter.deleteProjectProjection(null, LinkType.GITHUB);
        adapter.deleteProjectProjection(40L, null);
        adapter.deleteResourceProjection(null, LinkType.GITHUB, "resource");
        adapter.deleteResourceProjection(40L, null, "resource");
        adapter.deleteResourceProjection(40L, LinkType.GITHUB, " ");

        verifyNoInteractions(integrationActivityRepository, reportActivityLogRepository);
    }

    private IntegrationActivity activity(
            LinkType linkType,
            IntegrationActivityType activityType,
            ProjectMember projectMember,
            String providerEventKey,
            String actorLogin,
            String actorEmail,
            String providerPayload
    ) {
        return IntegrationActivity.builder()
                .id(30L)
                .integrationResource(resource(linkType))
                .projectMember(projectMember)
                .activityType(activityType)
                .providerEventKey(providerEventKey)
                .actorLogin(actorLogin)
                .actorEmail(actorEmail)
                .occurredAt(Instant.parse("2026-08-01T03:04:05Z"))
                .providerPayload(providerPayload)
                .build();
    }

    private IntegrationResource resource(LinkType linkType) {
        return IntegrationResource.builder()
                .id(10L)
                .projectIntegration(ProjectIntegration.builder()
                        .id(5L)
                        .project(project())
                        .linkType(linkType)
                        .credentialType(IntegrationCredentialType.OAUTH)
                        .externalAccountId("account-1")
                        .externalAccountName("account")
                        .providerConnectionId("connection-1")
                        .build())
                .resourceType(IntegrationResourceType.GITHUB_REPOSITORY)
                .providerResourceId("provider-resource-1")
                .resourceName("resource")
                .resourceStatus(IntegrationResourceStatus.ACTIVE)
                .build();
    }

    private Project project() {
        return Project.builder()
                .id(40L)
                .projectName("PLOG")
                .inviteTokenHash("invite-token-hash")
                .inviteTokenEncrypted("invite-token-encrypted")
                .projectType(ProjectType.DEVELOP)
                .status(ProjectStatus.IN_PROGRESS)
                .startDay(LocalDate.of(2026, 8, 1))
                .endDay(LocalDate.of(2026, 8, 31))
                .build();
    }
}
