package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.integration.entity.IntegrationCredentialType;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.entity.ProjectMemberIntegrationIdentity;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.integration.repository.ProjectMemberIntegrationIdentityRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.entity.RawActivityType;
import com.plog.domain.report.entity.ReliabilityTier;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.port.ExternalReportData;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExternalReportDataProviderImplTest {

    private static final Long PROJECT_ID = 40L;
    private static final Long MEMBER_1 = 1L;
    private static final Long MEMBER_2 = 2L;
    private int sourceRefSequence = 0;

    @Mock private ProjectIntegrationRepository projectIntegrationRepository;
    @Mock private ProjectMemberIntegrationIdentityRepository identityRepository;
    @Mock private ReportActivityLogRepository reportActivityLogRepository;

    private ExternalReportDataProviderImpl provider;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        provider = new ExternalReportDataProviderImpl(
                projectIntegrationRepository,
                identityRepository,
                reportActivityLogRepository,
                new ExternalActivityCompetencyMapper(objectMapper),
                objectMapper
        );
    }

    @Test
    void noActiveIntegrationReturnsNotConnectedWithoutLogQuery() {
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(PROJECT_ID)).thenReturn(List.of());

        Map<Long, ExternalReportData> result = provider.provide(PROJECT_ID, List.of(MEMBER_1, MEMBER_2));

        assertThat(result).containsOnlyKeys(MEMBER_1, MEMBER_2);
        assertThat(result.get(MEMBER_1).externalToolConnected()).isFalse();
        verify(identityRepository, never()).findActiveMappedIdentities(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(reportActivityLogRepository, never()).findExternalLogsForActiveProjectMembers(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mappedButNoPositiveScoreReturnsConnectedWithoutScoreAndNotionP3() {
        ProjectIntegration notion = integration(10L, LinkType.NOTION);
        ProjectMember member = member(MEMBER_1);
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(PROJECT_ID)).thenReturn(List.of(notion));
        when(identityRepository.findActiveMappedIdentities(List.of(10L), List.of(MEMBER_1), MemberStatus.ACTIVE))
                .thenReturn(List.of(identity(notion, member)));
        when(reportActivityLogRepository.findExternalLogsForActiveProjectMembers(List.of(MEMBER_1), externalDomains()))
                .thenReturn(List.of(log(member, LinkType.NOTION, RawActivityType.NOTION_COMMENT,
                        "{\"rich_text\":[{\"plain_text\":\"회의록 정리\"}]}")));

        ExternalReportData data = provider.provide(PROJECT_ID, List.of(MEMBER_1)).get(MEMBER_1);

        assertThat(data.externalToolConnected()).isTrue();
        assertThat(data.externalScoreAvailable()).isFalse();
        assertThat(data.externalScore()).isNull();
        assertThat(data.reliabilityTier()).isEqualTo(ReliabilityTier.P3);
        assertThat(data.activityCountByDomain()).containsEntry(SourceDomain.NOTION, 1L);
        assertThat(data.cautionText()).contains("Notion");
    }

    @Test
    void activeIntegrationReturnsNotMappedForOnlyUnmappedRequestedMember() {
        ProjectIntegration figma = integration(10L, LinkType.FIGMA);
        ProjectMember mappedMember = member(MEMBER_1);
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(PROJECT_ID)).thenReturn(List.of(figma));
        when(identityRepository.findActiveMappedIdentities(List.of(10L), List.of(MEMBER_1, MEMBER_2), MemberStatus.ACTIVE))
                .thenReturn(List.of(identity(figma, mappedMember)));
        when(reportActivityLogRepository.findExternalLogsForActiveProjectMembers(List.of(MEMBER_1), externalDomains()))
                .thenReturn(List.of(log(mappedMember, LinkType.FIGMA, RawActivityType.FIGMA_COMMENT,
                        "{\"message\":\"프로토타입 확인\"}")));

        Map<Long, ExternalReportData> result = provider.provide(PROJECT_ID, List.of(MEMBER_1, MEMBER_2));

        ExternalReportData mapped = result.get(MEMBER_1);
        ExternalReportData unmapped = result.get(MEMBER_2);
        assertThat(mapped.externalToolConnected()).isTrue();
        assertThat(mapped.externalScoreAvailable()).isTrue();
        assertThat(mapped.externalScore()).isEqualByComparingTo("100.00");
        assertThat(unmapped.externalToolConnected()).isFalse();
        assertThat(unmapped.externalScoreAvailable()).isFalse();
        assertThat(unmapped.externalScore()).isNull();
        assertThat(unmapped.cautionText()).contains("계정 매핑이 없어");
    }

    @Test
    void normalizesScoresAndDeduplicatesMergeCommitRegardlessOfInputOrder() {
        ProjectIntegration github = integration(10L, LinkType.GITHUB);
        ProjectMember member1 = member(MEMBER_1);
        ProjectMember member2 = member(MEMBER_2);
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(PROJECT_ID)).thenReturn(List.of(github));
        when(identityRepository.findActiveMappedIdentities(List.of(10L), List.of(MEMBER_1, MEMBER_2), MemberStatus.ACTIVE))
                .thenReturn(List.of(identity(github, member1), identity(github, member2)));
        when(reportActivityLogRepository.findExternalLogsForActiveProjectMembers(List.of(MEMBER_1, MEMBER_2), externalDomains()))
                .thenReturn(List.of(
                        log(member1, LinkType.GITHUB, RawActivityType.GITHUB_COMMIT, "{\"sha\":\"merge-sha\",\"message\":\"merge\"}"),
                        log(member1, LinkType.GITHUB, RawActivityType.GITHUB_PULL_REQUEST,
                                "{\"title\":\"기능 PR\",\"merge_commit_sha\":\"merge-sha\"}"),
                        log(member2, LinkType.GITHUB, RawActivityType.GITHUB_COMMIT, "{\"sha\":\"feature\",\"message\":\"작업\"}")
                ));

        Map<Long, ExternalReportData> result = provider.provide(PROJECT_ID, List.of(MEMBER_1, MEMBER_2));

        assertThat(result.get(MEMBER_1).externalScore()).isEqualByComparingTo("100.00");
        assertThat(result.get(MEMBER_2).externalScore()).isEqualByComparingTo("100.00");
        assertGithubMergeAggregation(result);
    }

    @Test
    void deduplicatesMergeCommitWhenPullRequestComesBeforeCommit() {
        ProjectIntegration github = integration(10L, LinkType.GITHUB);
        ProjectMember member1 = member(MEMBER_1);
        ProjectMember member2 = member(MEMBER_2);
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(PROJECT_ID)).thenReturn(List.of(github));
        when(identityRepository.findActiveMappedIdentities(List.of(10L), List.of(MEMBER_1, MEMBER_2), MemberStatus.ACTIVE))
                .thenReturn(List.of(identity(github, member1), identity(github, member2)));
        when(reportActivityLogRepository.findExternalLogsForActiveProjectMembers(List.of(MEMBER_1, MEMBER_2), externalDomains()))
                .thenReturn(List.of(
                        log(member1, LinkType.GITHUB, RawActivityType.GITHUB_PULL_REQUEST,
                                "{\"title\":\"기능 PR\",\"merge_commit_sha\":\"merge-sha\"}"),
                        log(member1, LinkType.GITHUB, RawActivityType.GITHUB_COMMIT, "{\"sha\":\"merge-sha\",\"message\":\"merge\"}"),
                        log(member2, LinkType.GITHUB, RawActivityType.GITHUB_COMMIT, "{\"sha\":\"feature\",\"message\":\"작업\"}")
                ));

        Map<Long, ExternalReportData> result = provider.provide(PROJECT_ID, List.of(MEMBER_1, MEMBER_2));

        assertThat(result.get(MEMBER_1).externalScore()).isEqualByComparingTo("100.00");
        assertThat(result.get(MEMBER_2).externalScore()).isEqualByComparingTo("100.00");
        assertGithubMergeAggregation(result);
    }

    @Test
    void capsFigmaReactionScorePerMemberDayBeforeTeamNormalization() {
        ProjectIntegration figma = integration(10L, LinkType.FIGMA);
        ProjectMember member1 = member(MEMBER_1);
        ProjectMember member2 = member(MEMBER_2);
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(PROJECT_ID)).thenReturn(List.of(figma));
        when(identityRepository.findActiveMappedIdentities(List.of(10L), List.of(MEMBER_1, MEMBER_2), MemberStatus.ACTIVE))
                .thenReturn(List.of(identity(figma, member1), identity(figma, member2)));
        when(reportActivityLogRepository.findExternalLogsForActiveProjectMembers(List.of(MEMBER_1, MEMBER_2), externalDomains()))
                .thenReturn(List.of(
                        log(member1, LinkType.FIGMA, RawActivityType.FIGMA_COMMENT_REACTION, "{\"emoji\":\"+1\"}"),
                        log(member1, LinkType.FIGMA, RawActivityType.FIGMA_COMMENT_REACTION, "{\"emoji\":\"heart\"}"),
                        log(member2, LinkType.FIGMA, RawActivityType.FIGMA_COMMENT_REACTION, "{\"emoji\":\"+1\"}")
                ));

        Map<Long, ExternalReportData> result = provider.provide(PROJECT_ID, List.of(MEMBER_1, MEMBER_2));

        assertThat(result.get(MEMBER_1).externalScore()).isEqualByComparingTo("100.00");
        assertThat(result.get(MEMBER_2).externalScore()).isEqualByComparingTo("100.00");
        assertThat(result.get(MEMBER_1).competencyActivityCount())
                .containsEntry(CompetencyCategory.COLLABORATION, 1L);
        assertThat(result.get(MEMBER_2).competencyActivityCount())
                .containsEntry(CompetencyCategory.COLLABORATION, 1L);
    }

    @Test
    void appliesFigmaReactionScoreCapAgainOnNextDay() {
        ProjectIntegration figma = integration(10L, LinkType.FIGMA);
        ProjectMember member1 = member(MEMBER_1);
        ProjectMember member2 = member(MEMBER_2);
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(PROJECT_ID)).thenReturn(List.of(figma));
        when(identityRepository.findActiveMappedIdentities(List.of(10L), List.of(MEMBER_1, MEMBER_2), MemberStatus.ACTIVE))
                .thenReturn(List.of(identity(figma, member1), identity(figma, member2)));
        when(reportActivityLogRepository.findExternalLogsForActiveProjectMembers(List.of(MEMBER_1, MEMBER_2), externalDomains()))
                .thenReturn(List.of(
                        log(member1, LinkType.FIGMA, RawActivityType.FIGMA_COMMENT_REACTION, "{\"emoji\":\"+1\"}",
                                LocalDateTime.of(2026, 8, 7, 10, 0)),
                        log(member1, LinkType.FIGMA, RawActivityType.FIGMA_COMMENT_REACTION, "{\"emoji\":\"heart\"}",
                                LocalDateTime.of(2026, 8, 7, 11, 0)),
                        log(member1, LinkType.FIGMA, RawActivityType.FIGMA_COMMENT_REACTION, "{\"emoji\":\"eyes\"}",
                                LocalDateTime.of(2026, 8, 8, 10, 0)),
                        log(member2, LinkType.FIGMA, RawActivityType.FIGMA_COMMENT, "{\"message\":\"레이아웃 확인\"}")
                ));

        Map<Long, ExternalReportData> result = provider.provide(PROJECT_ID, List.of(MEMBER_1, MEMBER_2));

        assertThat(result.get(MEMBER_1).externalScore()).isEqualByComparingTo("100.00");
        assertThat(result.get(MEMBER_2).externalScore()).isEqualByComparingTo("100.00");
        assertThat(result.get(MEMBER_1).competencyActivityCount())
                .containsEntry(CompetencyCategory.COLLABORATION, 2L);
    }

    @Test
    void separatesGoogleDocsAndSlidesByStableSourceRefPrefix() {
        ProjectIntegration docs = integration(10L, LinkType.GOOGLE_DOCS);
        ProjectIntegration slides = integration(11L, LinkType.GOOGLE_SLIDES);
        ProjectMember member = member(MEMBER_1);
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(PROJECT_ID)).thenReturn(List.of(docs, slides));
        when(identityRepository.findActiveMappedIdentities(List.of(10L, 11L), List.of(MEMBER_1), MemberStatus.ACTIVE))
                .thenReturn(List.of(identity(docs, member)));
        when(reportActivityLogRepository.findExternalLogsForActiveProjectMembers(List.of(MEMBER_1), externalDomains()))
                .thenReturn(List.of(
                        log(member, LinkType.GOOGLE_DOCS, RawActivityType.GOOGLE_DRIVE_REVISION,
                                "{\"originalFilename\":\"docs\"}"),
                        log(member, LinkType.GOOGLE_SLIDES, RawActivityType.GOOGLE_DRIVE_REVISION,
                                "{\"originalFilename\":\"slides\"}")
                ));

        ExternalReportData data = provider.provide(PROJECT_ID, List.of(MEMBER_1)).get(MEMBER_1);

        assertThat(data.activityCountByDomain()).containsEntry(SourceDomain.GOOGLE, 1L);
        assertThat(data.competencyEvidence().get(CompetencyCategory.OUTPUT))
                .singleElement().asString().contains("docs").doesNotContain("slides");
    }

    @Test
    void capsFigmaReactionScorePerMemberDayAndExcludesReactionEvidence() {
        ProjectIntegration figma = integration(10L, LinkType.FIGMA);
        ProjectMember member = member(MEMBER_1);
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(PROJECT_ID)).thenReturn(List.of(figma));
        when(identityRepository.findActiveMappedIdentities(List.of(10L), List.of(MEMBER_1), MemberStatus.ACTIVE))
                .thenReturn(List.of(identity(figma, member)));
        when(reportActivityLogRepository.findExternalLogsForActiveProjectMembers(List.of(MEMBER_1), externalDomains()))
                .thenReturn(List.of(
                        log(member, LinkType.FIGMA, RawActivityType.FIGMA_COMMENT_REACTION, "{\"emoji\":\"+1\"}"),
                        log(member, LinkType.FIGMA, RawActivityType.FIGMA_COMMENT_REACTION, "{\"emoji\":\"heart\"}"),
                        log(member, LinkType.FIGMA, RawActivityType.FIGMA_COMMENT, "{\"message\":\"레이아웃 확인\"}")
                ));

        ExternalReportData data = provider.provide(PROJECT_ID, List.of(MEMBER_1)).get(MEMBER_1);

        assertThat(data.externalScore()).isEqualByComparingTo("100.00");
        assertThat(data.competencyActivityCount()).containsEntry(CompetencyCategory.COLLABORATION, 2L);
        assertThat(data.competencyEvidence().get(CompetencyCategory.COLLABORATION))
                .singleElement().asString().contains("레이아웃 확인").doesNotContain("heart");
    }

    @Test
    void mappedNotionSnapshotsAreOutputEvidenceOnly() {
        ProjectIntegration notion = integration(10L, LinkType.NOTION);
        ProjectMember member = member(MEMBER_1);
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(PROJECT_ID)).thenReturn(List.of(notion));
        when(identityRepository.findActiveMappedIdentities(List.of(10L), List.of(MEMBER_1), MemberStatus.ACTIVE))
                .thenReturn(List.of(identity(notion, member)));
        when(reportActivityLogRepository.findExternalLogsForActiveProjectMembers(List.of(MEMBER_1), externalDomains()))
                .thenReturn(List.of(
                        logWithSourceRef(
                                member,
                                LinkType.NOTION,
                                RawActivityType.NOTION_PAGE_SNAPSHOT,
                                "{\"id\":\"page-1\",\"properties\":{\"이름\":{\"type\":\"title\",\"title\":[{\"plain_text\":\"API 명세 페이지\"}]}}}",
                                "notion-resource",
                                "page-snapshot"
                        ),
                        logWithSourceRef(
                                member,
                                LinkType.NOTION,
                                RawActivityType.NOTION_BLOCK_SNAPSHOT,
                                "{\"id\":\"parent-block-1\",\"parent\":{\"page_id\":\"page-1\"},\"type\":\"paragraph\",\"paragraph\":{\"rich_text\":[{\"plain_text\":\"배포 체크리스트\"}]}}",
                                "notion-resource",
                                "parent-block-snapshot-1"
                        ),
                        logWithSourceRef(
                                member,
                                LinkType.NOTION,
                                RawActivityType.NOTION_BLOCK_SNAPSHOT,
                                "{\"id\":\"nested-block-1\",\"parent\":{\"block_id\":\"parent-block-1\"},\"type\":\"paragraph\",\"paragraph\":{\"rich_text\":[{\"plain_text\":\"첫 중첩 블록\"}]}}",
                                "notion-resource",
                                "nested-block-snapshot-1"
                        ),
                        logWithSourceRef(
                                member,
                                LinkType.NOTION,
                                RawActivityType.NOTION_BLOCK_SNAPSHOT,
                                "{\"id\":\"parent-block-2\",\"parent\":{\"page_id\":\"page-1\"},\"type\":\"paragraph\",\"paragraph\":{\"rich_text\":[{\"plain_text\":\"검수 체크리스트\"}]}}",
                                "notion-resource",
                                "parent-block-snapshot-2"
                        ),
                        logWithSourceRef(
                                member,
                                LinkType.NOTION,
                                RawActivityType.NOTION_BLOCK_SNAPSHOT,
                                "{\"id\":\"nested-block-2\",\"parent\":{\"block_id\":\"parent-block-2\"},\"type\":\"paragraph\",\"paragraph\":{\"rich_text\":[{\"plain_text\":\"두 번째 중첩 블록\"}]}}",
                                "notion-resource",
                                "nested-block-snapshot-2"
                        )
                ));

        ExternalReportData data = provider.provide(PROJECT_ID, List.of(MEMBER_1)).get(MEMBER_1);

        assertThat(data.externalToolConnected()).isTrue();
        assertThat(data.externalScoreAvailable()).isFalse();
        assertThat(data.externalScore()).isNull();
        assertThat(data.reliabilityTier()).isEqualTo(ReliabilityTier.P3);
        assertThat(data.activityCountByDomain()).doesNotContainKey(SourceDomain.NOTION);
        assertThat(data.competencyActivityCount()).containsEntry(CompetencyCategory.OUTPUT, 0L);
        assertThat(data.competencyEvidence().get(CompetencyCategory.OUTPUT))
                .singleElement().asString().contains("API 명세 페이지");
        assertThat(data.competencyEvidence()).doesNotContainKeys(
                CompetencyCategory.COLLABORATION,
                CompetencyCategory.COMMUNICATION,
                CompetencyCategory.LEADERSHIP
        );
    }

    @Test
    void mappedP2ToolKeepsP2ReliabilityWhenOnlyNotionSnapshotEvidenceExists() {
        ProjectIntegration github = integration(10L, LinkType.GITHUB);
        ProjectIntegration notion = integration(11L, LinkType.NOTION);
        ProjectMember member = member(MEMBER_1);
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(PROJECT_ID))
                .thenReturn(List.of(github, notion));
        when(identityRepository.findActiveMappedIdentities(List.of(10L, 11L), List.of(MEMBER_1), MemberStatus.ACTIVE))
                .thenReturn(List.of(identity(github, member), identity(notion, member)));
        when(reportActivityLogRepository.findExternalLogsForActiveProjectMembers(List.of(MEMBER_1), externalDomains()))
                .thenReturn(List.of(logWithSourceRef(
                        member,
                        LinkType.NOTION,
                        RawActivityType.NOTION_PAGE_SNAPSHOT,
                        "{\"id\":\"page-1\",\"properties\":{\"이름\":{\"type\":\"title\",\"title\":[{\"plain_text\":\"API 명세 페이지\"}]}}}",
                        "notion-resource",
                        "page-snapshot"
                )));

        ExternalReportData data = provider.provide(PROJECT_ID, List.of(MEMBER_1)).get(MEMBER_1);

        assertThat(data.reliabilityTier()).isEqualTo(ReliabilityTier.P2);
        assertThat(data.externalScoreAvailable()).isFalse();
        assertThat(data.competencyEvidence().get(CompetencyCategory.OUTPUT)).hasSize(1);
    }

    @Test
    void selectsOneOutputEvidenceForCommitsInSamePullRequest() {
        ProjectIntegration github = integration(10L, LinkType.GITHUB);
        ProjectMember member1 = member(MEMBER_1);
        ProjectMember member2 = member(MEMBER_2);
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(PROJECT_ID)).thenReturn(List.of(github));
        when(identityRepository.findActiveMappedIdentities(List.of(10L), List.of(MEMBER_1, MEMBER_2), MemberStatus.ACTIVE))
                .thenReturn(List.of(identity(github, member1), identity(github, member2)));
        when(reportActivityLogRepository.findExternalLogsForActiveProjectMembers(
                List.of(MEMBER_1, MEMBER_2), externalDomains()))
                .thenReturn(List.of(
                        logWithSourceRef(member1, LinkType.GITHUB, RawActivityType.GITHUB_COMMIT,
                                "{\"sha\":\"commit-2\",\"commit\":{\"message\":\"두 번째 커밋\"},\"parents\":[{\"sha\":\"commit-1\"}]}",
                                "repo-a", "commit-2"),
                        logWithSourceRef(member1, LinkType.GITHUB, RawActivityType.GITHUB_COMMIT,
                                "{\"sha\":\"commit-1\",\"commit\":{\"message\":\"첫 번째 커밋\"},\"parents\":[{\"sha\":\"base\"}]}",
                                "repo-a", "commit-1"),
                        logWithSourceRef(member1, LinkType.GITHUB, RawActivityType.GITHUB_PULL_REQUEST,
                                "{\"number\":7,\"title\":\"인증 기능 PR\",\"head\":{\"sha\":\"commit-2\"},\"base\":{\"sha\":\"base\"}}",
                                "repo-a", "pull-request-7"),
                        logWithSourceRef(member1, LinkType.GITHUB, RawActivityType.GITHUB_PULL_REQUEST,
                                "{\"number\":8,\"title\":\"알림 기능 PR\"}",
                                "repo-a", "pull-request-8"),
                        logWithSourceRef(member2, LinkType.GITHUB, RawActivityType.GITHUB_COMMIT,
                                "{\"sha\":\"comparison\",\"commit\":{\"message\":\"비교 커밋\"}}",
                                "repo-a", "comparison")
                ));

        Map<Long, ExternalReportData> result = provider.provide(PROJECT_ID, List.of(MEMBER_1, MEMBER_2));
        ExternalReportData data = result.get(MEMBER_1);

        assertThat(data.activityCountByDomain()).containsEntry(SourceDomain.GITHUB, 4L);
        assertThat(data.competencyActivityCount()).containsEntry(CompetencyCategory.OUTPUT, 4L);
        assertThat(data.competencyEvidence().get(CompetencyCategory.OUTPUT)).hasSize(2);
        assertThat(data.externalScore()).isEqualByComparingTo("100.00");
        assertThat(result.get(MEMBER_2).externalScore()).isEqualByComparingTo("25.00");
    }

    @Test
    void selectsOneCommunicationEvidenceForSameGithubIssue() {
        ProjectIntegration github = integration(10L, LinkType.GITHUB);
        ProjectMember member = member(MEMBER_1);
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(PROJECT_ID)).thenReturn(List.of(github));
        when(identityRepository.findActiveMappedIdentities(List.of(10L), List.of(MEMBER_1), MemberStatus.ACTIVE))
                .thenReturn(List.of(identity(github, member)));
        when(reportActivityLogRepository.findExternalLogsForActiveProjectMembers(List.of(MEMBER_1), externalDomains()))
                .thenReturn(List.of(
                        logWithSourceRef(member, LinkType.GITHUB, RawActivityType.GITHUB_ISSUE,
                                "{\"number\":9,\"title\":\"로그인 오류\"}", "repo-a", "issue-9"),
                        logWithSourceRef(member, LinkType.GITHUB, RawActivityType.GITHUB_ISSUE_COMMENT,
                                "{\"issue_url\":\"https://api.github.com/repos/acme/repo/issues/9\",\"body\":\"원인 확인\"}",
                                "repo-a", "issue-comment-1"),
                        logWithSourceRef(member, LinkType.GITHUB, RawActivityType.GITHUB_ISSUE_COMMENT,
                                "{\"issue_url\":\"https://api.github.com/repos/acme/repo/issues/9\",\"body\":\"수정 완료\"}",
                                "repo-a", "issue-comment-2"),
                        logWithSourceRef(member, LinkType.GITHUB, RawActivityType.GITHUB_ISSUE,
                                "{\"number\":10,\"title\":\"배포 오류\"}", "repo-a", "issue-10")
                ));

        ExternalReportData data = provider.provide(PROJECT_ID, List.of(MEMBER_1)).get(MEMBER_1);

        assertThat(data.activityCountByDomain()).containsEntry(SourceDomain.GITHUB, 4L);
        assertThat(data.competencyActivityCount()).containsEntry(CompetencyCategory.COMMUNICATION, 4L);
        assertThat(data.competencyEvidence().get(CompetencyCategory.COMMUNICATION)).hasSize(2);
    }

    @Test
    void selectsOneCollaborationEvidenceForReviewsInSamePullRequest() {
        ProjectIntegration github = integration(10L, LinkType.GITHUB);
        ProjectMember member = member(MEMBER_1);
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(PROJECT_ID)).thenReturn(List.of(github));
        when(identityRepository.findActiveMappedIdentities(List.of(10L), List.of(MEMBER_1), MemberStatus.ACTIVE))
                .thenReturn(List.of(identity(github, member)));
        when(reportActivityLogRepository.findExternalLogsForActiveProjectMembers(List.of(MEMBER_1), externalDomains()))
                .thenReturn(List.of(
                        logWithSourceRef(member, LinkType.GITHUB, RawActivityType.GITHUB_PULL_REQUEST_REVIEW,
                                "{\"pull_request_url\":\"https://api.github.com/repos/acme/repo/pulls/7\",\"body\":\"첫 리뷰\"}",
                                "repo-a", "review-1"),
                        logWithSourceRef(member, LinkType.GITHUB, RawActivityType.GITHUB_PULL_REQUEST_REVIEW,
                                "{\"pull_request_url\":\"https://api.github.com/repos/acme/repo/pulls/7\",\"body\":\"두 번째 리뷰\"}",
                                "repo-a", "review-2"),
                        logWithSourceRef(member, LinkType.GITHUB, RawActivityType.GITHUB_PULL_REQUEST_REVIEW,
                                "{\"pull_request_url\":\"invalid\",\"html_url\":\"https://github.com/acme/repo/pull/8\",\"body\":\"다른 PR 리뷰\"}",
                                "repo-a", "review-3")
                ));

        ExternalReportData data = provider.provide(PROJECT_ID, List.of(MEMBER_1)).get(MEMBER_1);

        assertThat(data.activityCountByDomain()).containsEntry(SourceDomain.GITHUB, 3L);
        assertThat(data.competencyActivityCount()).containsEntry(CompetencyCategory.COLLABORATION, 3L);
        assertThat(data.competencyEvidence().get(CompetencyCategory.COLLABORATION)).hasSize(2);
    }

    @Test
    void selectsOneOutputEvidencePerGoogleDocument() {
        ProjectIntegration docs = integration(10L, LinkType.GOOGLE_DOCS);
        ProjectMember member = member(MEMBER_1);
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(PROJECT_ID)).thenReturn(List.of(docs));
        when(identityRepository.findActiveMappedIdentities(List.of(10L), List.of(MEMBER_1), MemberStatus.ACTIVE))
                .thenReturn(List.of(identity(docs, member)));
        when(reportActivityLogRepository.findExternalLogsForActiveProjectMembers(List.of(MEMBER_1), externalDomains()))
                .thenReturn(List.of(
                        logWithSourceRef(member, LinkType.GOOGLE_DOCS, RawActivityType.GOOGLE_DRIVE_REVISION,
                                "{\"originalFilename\":\"API 명세 v1\"}", "doc-a", "revision-1"),
                        logWithSourceRef(member, LinkType.GOOGLE_DOCS, RawActivityType.GOOGLE_DRIVE_REVISION,
                                "{\"originalFilename\":\"API 명세 v2\"}", "doc-a", "revision-2"),
                        logWithSourceRef(member, LinkType.GOOGLE_DOCS, RawActivityType.GOOGLE_DRIVE_REVISION,
                                "{\"originalFilename\":\"회의록\"}", "doc-b", "revision-1")
                ));

        ExternalReportData data = provider.provide(PROJECT_ID, List.of(MEMBER_1)).get(MEMBER_1);

        assertThat(data.activityCountByDomain()).containsEntry(SourceDomain.GOOGLE, 3L);
        assertThat(data.competencyActivityCount()).containsEntry(CompetencyCategory.OUTPUT, 3L);
        assertThat(data.competencyEvidence().get(CompetencyCategory.OUTPUT)).hasSize(2);
    }

    @Test
    void sanitizesEvidenceAndFallsBackWhenMetadataIsMalformed() {
        ProjectIntegration github = integration(10L, LinkType.GITHUB);
        ProjectMember member = member(MEMBER_1);
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(PROJECT_ID)).thenReturn(List.of(github));
        when(identityRepository.findActiveMappedIdentities(List.of(10L), List.of(MEMBER_1), MemberStatus.ACTIVE))
                .thenReturn(List.of(identity(github, member)));
        when(reportActivityLogRepository.findExternalLogsForActiveProjectMembers(List.of(MEMBER_1), externalDomains()))
                .thenReturn(List.of(
                        log(member, LinkType.GITHUB, RawActivityType.GITHUB_ISSUE_COMMENT,
                                "{\"body\":\"@decodeat https://example.com a@b.com 확인\"}"),
                        log(member, LinkType.GITHUB, RawActivityType.GITHUB_ISSUE, "{broken")
                ));

        ExternalReportData data = provider.provide(PROJECT_ID, List.of(MEMBER_1)).get(MEMBER_1);

        assertThat(data.competencyEvidence().values().stream().flatMap(List::stream).toList())
                .allSatisfy(text -> assertThat(text)
                        .doesNotContain("@decodeat")
                        .doesNotContain("https://example.com")
                        .doesNotContain("a@b.com"));
    }

    private ProjectIntegration integration(Long id, LinkType linkType) {
        return ProjectIntegration.builder()
                .id(id)
                .project(Project.builder()
                        .id(PROJECT_ID)
                        .projectName("Plog")
                        .inviteTokenHash("hash")
                        .inviteTokenEncrypted("encrypted")
                        .build())
                .connectedByProjectMember(member(999L))
                .linkType(linkType)
                .credentialType(IntegrationCredentialType.OAUTH)
                .externalAccountId("acc")
                .externalAccountName("account")
                .providerConnectionId("connected")
                .build();
    }

    private ProjectMemberIntegrationIdentity identity(ProjectIntegration integration, ProjectMember member) {
        return ProjectMemberIntegrationIdentity.builder()
                .projectIntegration(integration)
                .projectMember(member)
                .providerActorId("actor:" + member.getId())
                .build();
    }

    private ProjectMember member(Long id) {
        return ProjectMember.builder()
                .id(id)
                .status(MemberStatus.ACTIVE)
                .build();
    }

    private ReportActivityLog log(ProjectMember member, LinkType linkType, RawActivityType type, String metadata) {
        return log(member, linkType, type, metadata, LocalDateTime.of(2026, 8, 7, 10, 0));
    }

    private ReportActivityLog logWithSourceRef(
            ProjectMember member,
            LinkType linkType,
            RawActivityType type,
            String metadata,
            String resourceKey,
            String eventKey
    ) {
        return ReportActivityLog.create(
                member,
                type.owningDomain(),
                type,
                "content",
                LocalDateTime.of(2026, 8, 7, 10, 0),
                metadata,
                "integration:%s:%s:%s:%s".formatted(PROJECT_ID, linkType.name(), resourceKey, eventKey)
        );
    }

    private void assertGithubMergeAggregation(Map<Long, ExternalReportData> result) {
        assertThat(result.get(MEMBER_1).activityCountByDomain()).containsEntry(SourceDomain.GITHUB, 2L);
        assertThat(result.get(MEMBER_1).competencyActivityCount())
                .containsEntry(CompetencyCategory.OUTPUT, 2L)
                .containsEntry(CompetencyCategory.LEADERSHIP, 1L);
        assertThat(result.get(MEMBER_2).activityCountByDomain()).containsEntry(SourceDomain.GITHUB, 1L);
        assertThat(result.get(MEMBER_2).competencyActivityCount())
                .containsEntry(CompetencyCategory.OUTPUT, 1L);
    }

    private ReportActivityLog log(
            ProjectMember member,
            LinkType linkType,
            RawActivityType type,
            String metadata,
            LocalDateTime occurredAt
    ) {
        return ReportActivityLog.create(
                member,
                type.owningDomain(),
                type,
                "content",
                occurredAt,
                metadata,
                "integration:%s:%s:resource:event:%s".formatted(PROJECT_ID, linkType.name(), ++sourceRefSequence)
        );
    }

    private List<SourceDomain> externalDomains() {
        return List.of(SourceDomain.GITHUB, SourceDomain.FIGMA, SourceDomain.GOOGLE, SourceDomain.NOTION);
    }
}
