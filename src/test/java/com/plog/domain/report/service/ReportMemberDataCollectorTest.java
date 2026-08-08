package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.report.entity.ReliabilityTier;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.port.EvaluationSummaryProvider;
import com.plog.domain.report.port.ExternalReportData;
import com.plog.domain.report.port.ExternalReportDataProvider;
import com.plog.domain.report.port.InternalReportData;
import com.plog.domain.report.port.InternalReportDataProvider;
import com.plog.domain.report.port.PeerEvaluationSummary;
import com.plog.domain.report.port.SelfFeedbackMatchSummary;
import com.plog.domain.report.port.TaskSummary;
import com.plog.domain.report.entity.ReportMemberResult;
import com.plog.domain.report.repository.ReportRepository;
import com.plog.domain.task.entity.TaskCategory;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportMemberDataCollectorTest {

    private static final Long REPORT_ID = 20L;
    private static final Long PROJECT_ID = 1L;

    @Mock private InternalReportDataProvider internalProvider;
    @Mock private ExternalReportDataProvider externalProvider;
    @Mock private EvaluationSummaryProvider evaluationProvider;
    @Mock private ReportMemberScoreService memberScoreService;
    @Mock private ReportRepository reportRepository;

    private ReportMemberDataCollector collector;

    private final Project project = Project.builder()
            .id(PROJECT_ID)
            .projectName("Plog")
            .inviteTokenHash("invite-hash")
            .inviteTokenEncrypted("encrypted-invite")
            .build();

    private final ProjectMember member = ProjectMember.builder()
            .id(7L)
            .project(project)
            .user(User.createLocal("min@plog.test", "encoded", "송민", "송민"))
            .role(ProjectRole.MEMBER)
            .status(MemberStatus.ACTIVE)
            .build();

    @BeforeEach
    void setUp() {
        collector = new ReportMemberDataCollector(
                internalProvider, externalProvider, evaluationProvider, memberScoreService, reportRepository);
    }

    // 업무카드 요약(TaskSummary)의 metDeadline 개수를 세서 ReportMemberResult 에 저장하는지 검증한다.
    // "12/13건" 표기의 분자(deadlineMetTaskCount)가 여기서 만들어진다.
    @Test
    void 내부_데이터의_업무_집계를_ReportMemberResult에_반영한다() {
        Report report = Report.start(project);
        when(reportRepository.getReferenceById(REPORT_ID)).thenReturn(report);

        InternalReportData internal = new InternalReportData(
                List.of(
                        new TaskSummary("A", TaskCategory.DEVELOP, TaskStatus.DONE,
                                LocalDate.of(2026, 6, 1), true),
                        new TaskSummary("B", TaskCategory.DEVELOP, TaskStatus.DONE,
                                LocalDate.of(2026, 6, 2), false),
                        new TaskSummary("C", TaskCategory.DEVELOP, TaskStatus.IN_PROGRESS,
                                null, false)
                ),
                3, 2, 2 / 3.0, 1 / 3.0,
                List.of(), Map.of(), Map.of(), null
        );
        when(internalProvider.provide(PROJECT_ID, member.getId())).thenReturn(internal);
        when(externalProvider.provide(PROJECT_ID, member.getId())).thenReturn(ExternalReportData.notConnected());
        when(evaluationProvider.peer(PROJECT_ID, member.getId())).thenReturn(PeerEvaluationSummary.none());
        when(evaluationProvider.self(PROJECT_ID, member.getId())).thenReturn(SelfFeedbackMatchSummary.notSubmitted());

        ReportMemberResult result = ReportMemberResult.create(report, member);
        when(memberScoreService.calculateAndSave(any(), any(), any())).thenReturn(result);

        collector.collect(REPORT_ID, PROJECT_ID, ProjectType.DEVELOP, member, 5);

        assertThat(result.getTotalTaskCount()).isEqualTo(3);
        assertThat(result.getCompletedTaskCount()).isEqualTo(2);
        assertThat(result.getDeadlineMetTaskCount()).isEqualTo(1);
    }
}