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
import com.plog.domain.report.repository.ReportMemberResultRepository;
import com.plog.domain.report.repository.ReportRepository;
import com.plog.domain.report.repository.projection.ReportMemberSummary;
import com.plog.domain.user.entity.User;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.error.ReportErrorCode;
import com.plog.global.api.exception.ApiException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
        report.complete(LocalDateTime.of(2026, 7, 20, 12, 0));
        report.attachPdf("reports/20/report.pdf", "Plog-report.pdf");
        return report;
    }

    private Project project() {
        return Project.builder()
                .id(PROJECT_ID)
                .projectName("Plog")
                .inviteTokenHash("invite-hash")
                .inviteTokenEncrypted("encrypted-invite")
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
            public ReliabilityTier getReliabilityTier() {
                return ReliabilityTier.P1;
            }
        };
    }
}
