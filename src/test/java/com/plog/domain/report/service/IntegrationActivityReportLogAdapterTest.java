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
    void validMappedActivityIsProjectedToReportActivityLog() {
        IntegrationResource resource = resource(LinkType.GITHUB);
        ProjectMember member = ProjectMember.builder().id(63L).build();

        adapter.upsert(
                resource,
                member,
                IntegrationActivityType.GITHUB_COMMIT,
                "commit:abc123",
                "wantkdd",
                "wantkdd@example.com",
                Instant.parse("2026-08-01T03:04:05Z"),
                "{\"sha\":\"abc123\"}"
        );

        ArgumentCaptor<String> sourceRefId = ArgumentCaptor.forClass(String.class);
        verify(reportActivityLogRepository).upsertExternalActivityLog(
                eq(63L),
                eq("GITHUB"),
                eq("GITHUB_COMMIT"),
                eq(null),
                eq(LocalDateTime.of(2026, 8, 1, 3, 4, 5)),
                eq("{\"sha\":\"abc123\"}"),
                sourceRefId.capture()
        );
        assertThat(sourceRefId.getValue()).startsWith("integration:40:GITHUB:");
        assertThat(sourceRefId.getValue()).doesNotContain("commit:abc123");
    }

    @Test
    void sourceRefIdIsStableAcrossDatabaseResourceIdsForSameProviderResource() {
        ProjectMember member = ProjectMember.builder().id(63L).build();

        adapter.upsert(
                resource(LinkType.GITHUB, 10L),
                member,
                IntegrationActivityType.GITHUB_COMMIT,
                "commit:abc123",
                "wantkdd",
                "wantkdd@example.com",
                Instant.parse("2026-08-01T03:04:05Z"),
                "{}"
        );
        adapter.upsert(
                resource(LinkType.GITHUB, 11L),
                member,
                IntegrationActivityType.GITHUB_COMMIT,
                "commit:abc123",
                "wantkdd",
                "wantkdd@example.com",
                Instant.parse("2026-08-01T03:04:05Z"),
                "{}"
        );

        ArgumentCaptor<String> sourceRefId = ArgumentCaptor.forClass(String.class);
        verify(reportActivityLogRepository, times(2)).upsertExternalActivityLog(
                eq(63L),
                eq("GITHUB"),
                eq("GITHUB_COMMIT"),
                eq(null),
                eq(LocalDateTime.of(2026, 8, 1, 3, 4, 5)),
                eq("{}"),
                sourceRefId.capture()
        );
        assertThat(sourceRefId.getAllValues()).containsOnly(sourceRefId.getAllValues().get(0));
    }

    @Test
    void sameProviderEventKeyInDifferentProviderResourcesUsesDifferentSourceRefIds() {
        ProjectMember member = ProjectMember.builder().id(63L).build();

        adapter.upsert(
                resource(LinkType.GITHUB, 10L, "provider-resource-1"),
                member,
                IntegrationActivityType.GITHUB_COMMIT,
                "commit:abc123",
                "wantkdd",
                "wantkdd@example.com",
                Instant.parse("2026-08-01T03:04:05Z"),
                "{}"
        );
        adapter.upsert(
                resource(LinkType.GITHUB, 11L, "provider-resource-2"),
                member,
                IntegrationActivityType.GITHUB_COMMIT,
                "commit:abc123",
                "wantkdd",
                "wantkdd@example.com",
                Instant.parse("2026-08-01T03:04:05Z"),
                "{}"
        );

        ArgumentCaptor<String> sourceRefId = ArgumentCaptor.forClass(String.class);
        verify(reportActivityLogRepository, times(2)).upsertExternalActivityLog(
                eq(63L), eq("GITHUB"), eq("GITHUB_COMMIT"), eq(null),
                eq(LocalDateTime.of(2026, 8, 1, 3, 4, 5)), eq("{}"), sourceRefId.capture()
        );
        assertThat(sourceRefId.getAllValues()).doesNotHaveDuplicates();
    }

    @Test
    void githubBotIsSkippedButSimilarFigmaDisplayNameIsNotTreatedAsBot() {
        ProjectMember member = ProjectMember.builder().id(63L).build();

        adapter.upsert(
                resource(LinkType.GITHUB),
                member,
                IntegrationActivityType.GITHUB_COMMIT,
                "commit:bot",
                "dependabot[bot]",
                null,
                Instant.parse("2026-08-01T03:04:05Z"),
                "{}"
        );
        adapter.upsert(
                resource(LinkType.FIGMA),
                member,
                IntegrationActivityType.FIGMA_COMMENT,
                "comment:human",
                "Renovate Studio",
                null,
                Instant.parse("2026-08-01T03:04:05Z"),
                "{}"
        );

        verify(reportActivityLogRepository).upsertExternalActivityLog(
                eq(63L), eq("FIGMA"), eq("FIGMA_COMMENT"), eq(null),
                eq(LocalDateTime.of(2026, 8, 1, 3, 4, 5)), eq("{}"), sourceRefIdCaptor().capture()
        );
        verify(reportActivityLogRepository, times(1)).upsertExternalActivityLog(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void unassignedActivityIsProjectedWithNullProjectMember() {
        IntegrationResource resource = resource(LinkType.GOOGLE_DOCS);

        adapter.upsert(
                resource,
                null,
                IntegrationActivityType.GOOGLE_DRIVE_COMMENT,
                "comment:comment-1",
                "유상완",
                "sangwan@example.com",
                Instant.parse("2026-08-01T03:04:05Z"),
                "{\"id\":\"comment-1\"}"
        );

        verify(reportActivityLogRepository).upsertExternalActivityLog(
                eq(null),
                eq("GOOGLE"),
                eq("GOOGLE_DRIVE_COMMENT"),
                eq(null),
                eq(LocalDateTime.of(2026, 8, 1, 3, 4, 5)),
                eq("{\"id\":\"comment-1\"}"),
                sourceRefIdCaptor().capture()
        );
        verify(reportActivityLogRepository, never()).deleteExternalActivityLog(any(), any());
    }

    @Test
    void emptyCompetencyActivityIsSkippedWithoutDeletingProjectedLog() {
        IntegrationResource resource = resource(LinkType.FIGMA);
        ProjectMember member = ProjectMember.builder().id(63L).build();

        adapter.upsert(
                resource,
                member,
                IntegrationActivityType.FIGMA_FILE_METADATA,
                "metadata:file-1",
                null,
                null,
                Instant.parse("2026-08-01T03:04:05Z"),
                "{}"
        );

        verify(reportActivityLogRepository, never()).deleteExternalActivityLog(any(), any());
        verify(reportActivityLogRepository, never()).upsertExternalActivityLog(
                any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void googleDriveMoveActionIsSkippedWithoutDeletingProjectedLog() {
        IntegrationResource resource = resource(LinkType.GOOGLE_DOCS);
        ProjectMember member = ProjectMember.builder().id(63L).build();

        adapter.upsert(
                resource,
                member,
                IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY,
                "drive-activity:event-1",
                "유상완",
                "sangwan@example.com",
                Instant.parse("2026-08-01T03:04:05Z"),
                "{\"action\":\"move\"}"
        );

        verify(reportActivityLogRepository, never()).deleteExternalActivityLog(any(), any());
        verify(reportActivityLogRepository, never()).upsertExternalActivityLog(
                any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void outsideProjectPeriodActivityIsSkippedWithoutDeletingProjectedLog() {
        IntegrationResource resource = resource(LinkType.NOTION);
        ProjectMember member = ProjectMember.builder().id(63L).build();

        adapter.upsert(
                resource,
                member,
                IntegrationActivityType.NOTION_COMMENT,
                "comment:notion-comment-1",
                "유상완",
                "sangwan@example.com",
                Instant.parse("2026-09-01T03:04:05Z"),
                "{\"id\":\"notion-comment-1\"}"
        );

        verify(reportActivityLogRepository, never()).deleteExternalActivityLog(any(), any());
        verify(reportActivityLogRepository, never()).upsertExternalActivityLog(
                any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void synchronizeActivityFetchesSingleProjectionTarget() {
        IntegrationActivity activity = activity();
        when(integrationActivityRepository.findReportProjectionTarget(10L, "commit:abc123"))
                .thenReturn(Optional.of(activity));

        adapter.synchronizeActivity(10L, "commit:abc123");

        verify(integrationActivityRepository).findReportProjectionTarget(10L, "commit:abc123");
        verify(reportActivityLogRepository).upsertExternalActivityLog(
                eq(63L),
                eq("GITHUB"),
                eq("GITHUB_COMMIT"),
                eq(null),
                eq(LocalDateTime.of(2026, 8, 1, 3, 4, 5)),
                eq("{\"sha\":\"abc123\"}"),
                sourceRefIdCaptor().capture()
        );
    }

    @Test
    void synchronizeActivityProjectsFetchedActivityEvenWhenMemberIsStillUnmapped() {
        IntegrationActivity activity = IntegrationActivity.builder()
                .id(30L)
                .integrationResource(resource(LinkType.GOOGLE_DOCS))
                .projectMember(null)
                .activityType(IntegrationActivityType.GOOGLE_DRIVE_COMMENT)
                .providerEventKey("comment:comment-1")
                .actorLogin("유상완")
                .actorEmail("sangwan@example.com")
                .occurredAt(Instant.parse("2026-08-01T03:04:05Z"))
                .providerPayload("{\"id\":\"comment-1\"}")
                .build();
        when(integrationActivityRepository.findReportProjectionTarget(10L, "comment:comment-1"))
                .thenReturn(Optional.of(activity));

        adapter.synchronizeActivity(10L, "comment:comment-1");

        verify(reportActivityLogRepository).upsertExternalActivityLog(
                eq(null),
                eq("GOOGLE"),
                eq("GOOGLE_DRIVE_COMMENT"),
                eq(null),
                eq(LocalDateTime.of(2026, 8, 1, 3, 4, 5)),
                eq("{\"id\":\"comment-1\"}"),
                sourceRefIdCaptor().capture()
        );
    }

    @Test
    void synchronizeProjectMemberFetchesOnlyMemberProjectionTargetsOnce() {
        when(integrationActivityRepository.findReportProjectionTargetsByMember(5L, 63L))
                .thenReturn(List.of(activity()));

        adapter.synchronizeProjectMemberActivities(5L, 63L);

        verify(integrationActivityRepository).findReportProjectionTargetsByMember(5L, 63L);
        verify(reportActivityLogRepository).upsertExternalActivityLog(
                eq(63L),
                eq("GITHUB"),
                eq("GITHUB_COMMIT"),
                eq(null),
                eq(LocalDateTime.of(2026, 8, 1, 3, 4, 5)),
                eq("{\"sha\":\"abc123\"}"),
                sourceRefIdCaptor().capture()
        );
    }

    @Test
    void deleteProjectMemberProjectionDeletesOnlyMatchingMemberAndSourcePrefix() {
        adapter.deleteProjectMemberProjection(40L, LinkType.GITHUB, 63L);

        verify(reportActivityLogRepository).deleteExternalActivityLogsByMemberAndSourcePrefix(
                63L,
                "GITHUB",
                "integration:40:GITHUB:"
        );
    }

    @Test
    void deleteProjectAndResourceProjectionUseScopedSourcePrefixes() {
        adapter.deleteProjectProjection(40L, LinkType.GOOGLE_DOCS);
        adapter.deleteResourceProjection(40L, LinkType.GOOGLE_DOCS, "provider-resource-1");

        ArgumentCaptor<String> prefix = ArgumentCaptor.forClass(String.class);
        verify(reportActivityLogRepository, times(2)).deleteExternalActivityLogsBySourcePrefix(
                eq("GOOGLE"), prefix.capture());
        assertThat(prefix.getAllValues().get(0)).isEqualTo("integration:40:GOOGLE_DOCS:");
        assertThat(prefix.getAllValues().get(1))
                .startsWith("integration:40:GOOGLE_DOCS:")
                .isNotEqualTo(prefix.getAllValues().get(0));
    }

    @Test
    void blankSynchronizationRequestDoesNothing() {
        adapter.synchronizeActivity(null, "commit:abc123");
        adapter.synchronizeActivity(10L, " ");
        adapter.synchronizeProjectMemberActivities(null, 63L);
        adapter.synchronizeProjectMemberActivities(5L, null);
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

    private IntegrationActivity activity() {
        return IntegrationActivity.builder()
                .id(30L)
                .integrationResource(resource(LinkType.GITHUB))
                .projectMember(ProjectMember.builder().id(63L).build())
                .activityType(IntegrationActivityType.GITHUB_COMMIT)
                .providerEventKey("commit:abc123")
                .actorLogin("wantkdd")
                .actorEmail("wantkdd@example.com")
                .occurredAt(Instant.parse("2026-08-01T03:04:05Z"))
                .providerPayload("{\"sha\":\"abc123\"}")
                .build();
    }

    private IntegrationResource resource(LinkType linkType) {
        return resource(linkType, 10L);
    }

    private IntegrationResource resource(LinkType linkType, Long resourceId) {
        return resource(linkType, resourceId, "provider-resource-1");
    }

    private IntegrationResource resource(LinkType linkType, Long resourceId, String providerResourceId) {
        return IntegrationResource.builder()
                .id(resourceId)
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
                .providerResourceId(providerResourceId)
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

    private ArgumentCaptor<String> sourceRefIdCaptor() {
        return ArgumentCaptor.forClass(String.class);
    }
}
