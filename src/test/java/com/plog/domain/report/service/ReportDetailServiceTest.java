package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.report.dto.response.ReportDetailResponse;
import com.plog.domain.report.dto.response.ReportMemberResultResponse;
import com.plog.domain.report.entity.ReliabilityTier;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.ReportMemberResult;
import com.plog.domain.report.entity.ReportStatus;
import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.repository.ReportMemberResultRepository;
import com.plog.domain.report.repository.ReportRepository;
import com.plog.domain.report.repository.projection.ReportMemberSummary;
import com.plog.domain.user.entity.User;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.error.ReportErrorCode;
import com.plog.global.api.exception.ApiException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReportDetailServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PROJECT_ID = 10L;
    private static final Long REPORT_ID = 20L;
    private static final Long PROJECT_MEMBER_ID = 7L;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ReportMemberResultRepository memberResultRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ReportDetailService reportDetailService;

    @Test
    void returnsCompletedReportWithMemberSummariesOrderedByRepository() {
        Report report = completedReport();
        when(reportRepository.findWithProjectById(REPORT_ID)).thenReturn(Optional.of(report));
        when(memberResultRepository.findMemberSummaries(REPORT_ID))
                .thenReturn(List.of(memberSummary(PROJECT_MEMBER_ID, "창훈", new BigDecimal("82.50"))));

        ReportDetailResponse response = reportDetailService.getReport(USER_ID, REPORT_ID);

        assertThat(response.status()).isEqualTo(ReportStatus.COMPLETED);
        assertThat(response.projectName()).isEqualTo("Plog");
        assertThat(response.pdfAvailable()).isTrue();
        assertThat(response.members()).hasSize(1);
        assertThat(response.members().getFirst().memberName()).isEqualTo("창훈");
        assertThat(response.members().getFirst().finalScore()).isEqualByComparingTo("82.50");
        assertThat(response.members().getFirst().contributionRate()).isEqualByComparingTo("25.00");
        assertThat(response.projectStartDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(response.projectEndDate()).isEqualTo(LocalDate.of(2026, 6, 12));
        assertThat(response.totalTaskCount()).isEqualTo(4);
        assertThat(response.completedTaskCount()).isEqualTo(3);
        assertThat(response.deadlineMetTaskCount()).isEqualTo(2);
        assertThat(response.deadlineTargetTaskCount()).isEqualTo(3);
        assertThat(response.memberCount()).isEqualTo(1);
        assertThat(response.members().getFirst().peerAverage()).isEqualByComparingTo("4.25");
        verify(projectAccessService).requireActiveMember(PROJECT_ID, USER_ID);
    }

    // 발행 전에는 멤버 결과가 채워졌다는 보장이 없다 → 쿼리 자체를 하지 않고 빈 배열로 내려간다.
    @Test
    void returnsGeneratingReportWithoutQueryingMemberSummaries() {
        Report report = Report.start(project());
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        when(reportRepository.findWithProjectById(REPORT_ID)).thenReturn(Optional.of(report));

        ReportDetailResponse response = reportDetailService.getReport(USER_ID, REPORT_ID);

        assertThat(response.status()).isEqualTo(ReportStatus.GENERATING);
        assertThat(response.completedAt()).isNull();
        assertThat(response.pdfAvailable()).isFalse();
        assertThat(response.members()).isEmpty();
        verify(memberResultRepository, never()).findMemberSummaries(REPORT_ID);
    }

    // COMPLETED 라도 PDF 가 아직 안 붙었으면 다운로드 버튼이 뜨면 안 된다.
    @Test
    void marksPdfUnavailableWhenCompletedReportHasNoPdfMetadata() {
        Report report = Report.start(project());
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        report.complete(LocalDateTime.of(2026, 7, 20, 12, 0));
        when(reportRepository.findWithProjectById(REPORT_ID)).thenReturn(Optional.of(report));
        when(memberResultRepository.findMemberSummaries(REPORT_ID)).thenReturn(List.of());

        ReportDetailResponse response = reportDetailService.getReport(USER_ID, REPORT_ID);

        assertThat(response.status()).isEqualTo(ReportStatus.COMPLETED);
        assertThat(response.pdfAvailable()).isFalse();
    }

    @Test
    void returnsMemberResultWithDisplayNickname() {
        Report report = completedReport();
        when(reportRepository.findWithProjectById(REPORT_ID)).thenReturn(Optional.of(report));
        when(memberResultRepository.findWithMemberByReportIdAndProjectMemberId(REPORT_ID, PROJECT_MEMBER_ID))
                .thenReturn(Optional.of(memberResult(report)));

        ReportMemberResultResponse response = reportDetailService.getMemberResult(
                USER_ID, REPORT_ID, PROJECT_MEMBER_ID);

        assertThat(response.reportId()).isEqualTo(REPORT_ID);
        assertThat(response.projectMemberId()).isEqualTo(PROJECT_MEMBER_ID);
        assertThat(response.memberName()).isEqualTo("창훈");
        assertThat(response.finalScore()).isEqualByComparingTo("82.50");
        assertThat(response.externalToolConnected()).isFalse();
        assertThat(response.externalScore()).isNull();
        assertThat(response.reliabilityTier()).isEqualTo(ReliabilityTier.P2);
        assertThat(response.reportCode()).startsWith("PLOG-");
        assertThat(response.projectName()).isEqualTo("Plog");
        assertThat(response.projectStartDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(response.competencyScores100().get(CompetencyCategory.COLLABORATION))
                .isEqualByComparingTo("88.00");
    }

    // jsonb 컬럼은 문자열로 저장돼 있다 — 응답에 그대로 실으면 프론트가 한 번 더 파싱해야 한다.
    @Test
    void deserializesJsonColumnsIntoObjects() {
        Report report = completedReport();
        ReportMemberResult result = memberResult(report);
        result.applyLlmText(new ReportMemberResult.LlmTextPayload(
                "적극적인 리더십으로 팀의 방향을 잡았어요",
                "[{\"title\":\"주도성\",\"description\":\"일정을 주도적으로 관리해요\"}]",
                "{\"title\":\"의견 제시 빈도가 낮음\",\"suggestions\":[\"의견을 늘려보세요\"]}",
                "{\"growthPoint\":\"성장\",\"keepStrength\":\"유지\",\"nextAction\":\"액션\"}",
                "{\"coverLetter\":\"자소서\",\"portfolio\":\"포폴\"}",
                "{}",
                "gemini-2.5-flash"
        ));
        when(reportRepository.findWithProjectById(REPORT_ID)).thenReturn(Optional.of(report));
        when(memberResultRepository.findWithMemberByReportIdAndProjectMemberId(REPORT_ID, PROJECT_MEMBER_ID))
                .thenReturn(Optional.of(result));

        ReportMemberResultResponse response = reportDetailService.getMemberResult(
                USER_ID, REPORT_ID, PROJECT_MEMBER_ID);

        assertThat(response.headline()).isEqualTo("적극적인 리더십으로 팀의 방향을 잡았어요");
        assertThat(response.strengths()).hasSize(1);
        assertThat(response.strengths().getFirst().title()).isEqualTo("주도성");
        assertThat(response.weakness().suggestions()).containsExactly("의견을 늘려보세요");
        assertThat(response.growth().nextAction()).isEqualTo("액션");
        assertThat(response.writing().portfolio()).isEqualTo("포폴");
    }

    // LLM 실패로 텍스트가 비어도 점수는 내려가야 한다(부분 실패 정책).
    @Test
    void returnsScoresEvenWhenLlmTextIsMissing() {
        Report report = completedReport();
        when(reportRepository.findWithProjectById(REPORT_ID)).thenReturn(Optional.of(report));
        when(memberResultRepository.findWithMemberByReportIdAndProjectMemberId(REPORT_ID, PROJECT_MEMBER_ID))
                .thenReturn(Optional.of(memberResult(report)));

        ReportMemberResultResponse response = reportDetailService.getMemberResult(
                USER_ID, REPORT_ID, PROJECT_MEMBER_ID);

        assertThat(response.finalScore()).isEqualByComparingTo("82.50");
        assertThat(response.headline()).isNull();
        assertThat(response.strengths()).isEmpty();
        assertThat(response.weakness()).isNull();
    }

    // 저장된 JSON 이 손상돼도 조회 전체가 실패하면 안 된다 — 점수와 나머지 섹션은 보여줘야 한다.
    @Test
    void keepsOtherSectionsWhenOneJsonColumnIsCorrupted() {
        Report report = completedReport();
        ReportMemberResult result = memberResult(report);
        result.applyLlmText(new ReportMemberResult.LlmTextPayload(
                "한 줄", "깨진 JSON", "{\"title\":\"약점\",\"suggestions\":[]}", null, null, null, null));
        when(reportRepository.findWithProjectById(REPORT_ID)).thenReturn(Optional.of(report));
        when(memberResultRepository.findWithMemberByReportIdAndProjectMemberId(REPORT_ID, PROJECT_MEMBER_ID))
                .thenReturn(Optional.of(result));

        ReportMemberResultResponse response = reportDetailService.getMemberResult(
                USER_ID, REPORT_ID, PROJECT_MEMBER_ID);

        assertThat(response.strengths()).isEmpty();
        assertThat(response.weakness().title()).isEqualTo("약점");
        assertThat(response.headline()).isEqualTo("한 줄");
    }

    @Test
    void exposesTeamInsightOnTheReportDetail() {
        // 팀 인사이트는 생성 중에 기록되고 그 뒤 발행된다 — 실제 파이프라인 순서와 같게 만든다.
        Report report = Report.start(project());
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        report.applyTeamInsight("팀이 잘한 점", "앞으로는 이렇게");
        report.complete(LocalDateTime.of(2026, 7, 20, 12, 0));
        when(reportRepository.findWithProjectById(REPORT_ID)).thenReturn(Optional.of(report));
        when(memberResultRepository.findMemberSummaries(REPORT_ID)).thenReturn(List.of());

        ReportDetailResponse response = reportDetailService.getReport(USER_ID, REPORT_ID);

        assertThat(response.teamStrength()).isEqualTo("팀이 잘한 점");
        assertThat(response.teamSuggestion()).isEqualTo("앞으로는 이렇게");
    }

    @Test
    void rejectsMissingReport() {
        when(reportRepository.findWithProjectById(REPORT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportDetailService.getReport(USER_ID, REPORT_ID))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ReportErrorCode.REPORT_NOT_FOUND);
    }

    @Test
    void rejectsMissingMemberResult() {
        when(reportRepository.findWithProjectById(REPORT_ID)).thenReturn(Optional.of(completedReport()));
        when(memberResultRepository.findWithMemberByReportIdAndProjectMemberId(REPORT_ID, PROJECT_MEMBER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportDetailService.getMemberResult(USER_ID, REPORT_ID, PROJECT_MEMBER_ID))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ReportErrorCode.REPORT_MEMBER_RESULT_NOT_FOUND);
    }

    // 다른 팀 사람이 리포트 ID 만 알고 찔러도 프로젝트 멤버십에서 막혀야 한다.
    @Test
    void rejectsNonMemberOfTheReportProject() {
        when(reportRepository.findWithProjectById(REPORT_ID)).thenReturn(Optional.of(completedReport()));
        when(projectAccessService.requireActiveMember(PROJECT_ID, USER_ID))
                .thenThrow(new ApiException(ProjectErrorCode.PROJECT_MEMBER_REQUIRED));

        assertThatThrownBy(() -> reportDetailService.getReport(USER_ID, REPORT_ID))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ProjectErrorCode.PROJECT_MEMBER_REQUIRED);
        verify(memberResultRepository, never()).findMemberSummaries(REPORT_ID);
    }

    private Report completedReport() {
        Report report = Report.start(project());
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        ReflectionTestUtils.setField(report, "createdAt", LocalDateTime.of(2026, 7, 20, 11, 0));
        report.complete(LocalDateTime.of(2026, 7, 20, 12, 0));
        report.attachPdf("reports/20/reports.zip", "Plog-reports.zip");
        return report;
    }

    private Project project() {
        return Project.builder()
                .id(PROJECT_ID)
                .projectName("Plog")
                .inviteTokenHash("invite-hash")
                .inviteTokenEncrypted("encrypted-invite")
                .startDay(LocalDate.of(2026, 5, 1))
                .endDay(LocalDate.of(2026, 6, 12))
                .build();
    }

    private ReportMemberResult memberResult(Report report) {
        ProjectMember member = ProjectMember.builder()
                .id(PROJECT_MEMBER_ID)
                .project(report.getProject())
                .user(User.createLocal("chang@plog.test", "encoded", "이창훈", "창훈"))
                .role(ProjectRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .build();
        ReportMemberResult result = ReportMemberResult.create(report, member);
        result.applyScores(
                new BigDecimal("88.00"),
                null,
                new BigDecimal("80.00"),
                new BigDecimal("70.00"),
                new BigDecimal("82.50"),
                false,
                ReliabilityTier.P2,
                "Notion이 연동되지 않아 일부 작업 과정은 반영되지 않았을 수 있습니다."
        );
        result.applyTaskStatistics(2, 2, 2, 2, 1.0, 1.0);
        result.applyPeerBreakdown(new BigDecimal("4.25"),
                Map.of(CompetencyCategory.COLLABORATION, new BigDecimal("4.40")),
                List.of("책임감"));
        return result;
    }

    private ReportMemberSummary memberSummary(Long memberId, String name, BigDecimal finalScore) {
        return new ReportMemberSummary() {
            @Override
            public Long getProjectMemberId() {
                return memberId;
            }

            @Override
            public String getMemberName() {
                return name;
            }

            @Override
            public BigDecimal getFinalScore() {
                return finalScore;
            }

            @Override
            public BigDecimal getContributionRate() {
                return new BigDecimal("25.00");
            }

            @Override
            public ReliabilityTier getReliabilityTier() {
                return ReliabilityTier.P1;
            }

            @Override
            public com.plog.domain.user.entity.ProfilePreset getProfilePreset() {
                return com.plog.domain.user.entity.ProfilePreset.OTTER;
            }

            @Override public int getTotalTaskCount() { return 4; }
            @Override public int getCompletedTaskCount() { return 3; }
            @Override public int getDeadlineMetTaskCount() { return 2; }
            @Override public int getDeadlineTargetTaskCount() { return 3; }
            @Override public BigDecimal getCompletionRate() { return new BigDecimal("75.00"); }
            @Override public BigDecimal getDeadlineComplianceRate() { return new BigDecimal("66.67"); }
            @Override public BigDecimal getPeerAverage() { return new BigDecimal("4.25"); }
            @Override public Map<CompetencyCategory, BigDecimal> getCompetencyScores() {
                return Map.of(CompetencyCategory.COLLABORATION, new BigDecimal("4.40"));
            }
            @Override public List<String> getPeerKeywords() { return List.of("책임감"); }

            @Override
            public String getHeadline() {
                return "적극적인 리더십으로 팀의 방향을 잡았어요";
            }
        };
    }
}
