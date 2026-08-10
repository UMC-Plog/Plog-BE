package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.notification.event.ReportPublishedEvent;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.llm.MemberLlmInput;
import com.plog.domain.report.llm.MemberReportText;
import com.plog.domain.report.llm.ReportLlmGateway;
import com.plog.domain.report.llm.TeamLlmInput;
import com.plog.domain.report.llm.TeamReportText;
import com.plog.domain.report.port.InternalReportData;
import com.plog.domain.report.port.ExternalReportDataProvider;
import com.plog.domain.report.repository.ReportRepository;
import com.plog.global.api.error.ReportErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.infrastructure.ai.LlmGenerationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportGenerationServiceTest {

    private static final Long REPORT_ID = 20L;
    private static final Long PROJECT_ID = 10L;

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private ExternalReportDataProvider externalDataProvider;
    @Mock
    private ReportMemberDataCollector dataCollector;
    @Mock
    private ReportTextWriter textWriter;
    @Mock
    private ReportTeamMetricService teamMetricService;
    @Mock
    private ReportLlmGateway llmGateway;
    @Mock
    private ReportPdfArchiveService pdfArchiveService;
    @Mock
    private ReportActivityPreparationService activityPreparationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ReportGenerationService service;

    @Test
    void generatesTextForEveryMemberThenPublishesAndNotifies() {
        givenReportWithMembers(1L, 2L);
        givenCollectionSucceeds();
        when(llmGateway.generateMemberText(any())).thenReturn(generatedText("한 줄 평가"));
        when(llmGateway.generateTeamText(any())).thenReturn(new TeamReportText("잘함", "제안"));

        ReportGenerationResult result = service.generate(REPORT_ID);

        assertThat(result.published()).isTrue();
        assertThat(result.memberCount()).isEqualTo(2);
        assertThat(result.textSucceeded()).isEqualTo(2);
        assertThat(result.isPartial()).isFalse();
        verify(textWriter).writeMemberText(eq(REPORT_ID), eq(1L), any());
        verify(textWriter).writeMemberText(eq(REPORT_ID), eq(2L), any());
        verify(textWriter).writeTeamInsight(eq(REPORT_ID), any(TeamReportText.class));
        verify(textWriter).publish(REPORT_ID);
        verify(eventPublisher).publishEvent(new ReportPublishedEvent(PROJECT_ID, REPORT_ID));
        verify(externalDataProvider).provide(eq(PROJECT_ID), eq(List.of(1L, 2L)), any());
    }

    // 승인된 실패 정책 — 멤버 1명 LLM 실패는 그 멤버 텍스트만 비우고 리포트는 발행한다.
    @Test
    void publishesEvenWhenOneMemberTextFails() {
        givenReportWithMembers(1L, 2L);
        givenCollectionSucceeds();
        when(llmGateway.generateMemberText(any()))
                .thenThrow(new LlmGenerationException("boom"))
                .thenReturn(generatedText("두 번째 멤버"));
        when(llmGateway.generateTeamText(any())).thenReturn(new TeamReportText("잘함", "제안"));

        ReportGenerationResult result = service.generate(REPORT_ID);

        assertThat(result.published()).isTrue();
        assertThat(result.textSucceeded()).isEqualTo(1);
        assertThat(result.isPartial()).isTrue();
        // 실패한 멤버는 텍스트가 없고, 뒤에 온 멤버는 정상 처리된다.
        verify(textWriter, never()).writeMemberText(eq(REPORT_ID), eq(1L), any());
        verify(textWriter).writeMemberText(eq(REPORT_ID), eq(2L), any());
        verify(textWriter).publish(REPORT_ID);
    }

    @Test
    void failsTheReportWhenEveryMemberTextFails() {
        givenReportWithMembers(1L, 2L);
        givenCollectionSucceeds();
        when(llmGateway.generateMemberText(any())).thenThrow(new LlmGenerationException("boom"));

        ReportGenerationResult result = service.generate(REPORT_ID);

        assertThat(result.published()).isFalse();
        verify(textWriter).markFailed(REPORT_ID);
        verify(textWriter, never()).publish(anyLong());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // 팀 인사이트는 부가 정보다 — 실패해도 멤버별 결과가 있으면 발행한다.
    @Test
    void publishesEvenWhenTeamInsightFails() {
        givenReportWithMembers(1L);
        givenCollectionSucceeds();
        when(llmGateway.generateMemberText(any())).thenReturn(generatedText("한 줄"));
        when(llmGateway.generateTeamText(any())).thenThrow(new LlmGenerationException("팀 실패"));

        ReportGenerationResult result = service.generate(REPORT_ID);

        assertThat(result.published()).isTrue();
        verify(textWriter, never()).writeTeamInsight(anyLong(), any());
        verify(textWriter).publish(REPORT_ID);
    }

    // 점수 수집이 깨진 멤버는 건너뛰되 나머지는 계속 간다.
    @Test
    void skipsMembersWhoseDataCollectionFails() {
        givenReportWithMembers(1L, 2L);
        when(dataCollector.collect(anyLong(), anyLong(), any(), any(), any(), anyInt(), any()))
                .thenThrow(new IllegalStateException("포트 실패"))
                .thenReturn(collected(2L, internalWithRates()));
        when(llmGateway.generateMemberText(any())).thenReturn(generatedText("두 번째"));
        when(llmGateway.generateTeamText(any())).thenReturn(new TeamReportText("잘함", "제안"));

        ReportGenerationResult result = service.generate(REPORT_ID);

        assertThat(result.published()).isTrue();
        assertThat(result.textSucceeded()).isEqualTo(1);
        verify(textWriter).writeMemberText(eq(REPORT_ID), eq(2L), any());

        org.mockito.ArgumentCaptor<TeamLlmInput> captor =
                org.mockito.ArgumentCaptor.forClass(TeamLlmInput.class);
        verify(llmGateway).generateTeamText(captor.capture());
        assertThat(captor.getValue().teamCompletionRate()).isNull();
        assertThat(captor.getValue().teamDeadlineComplianceRate()).isNull();
    }

    @Test
    void 팀_지표_계산이_실패해도_개인_리포트를_생성하고_발행한다() {
        givenReportWithMembers(1L);
        givenCollectionSucceeds();
        when(teamMetricService.calculateAndApply(REPORT_ID, 1))
                .thenThrow(new IllegalStateException("팀 지표 실패"));
        when(llmGateway.generateMemberText(any())).thenReturn(generatedText("한 줄"));
        when(llmGateway.generateTeamText(any())).thenReturn(new TeamReportText("잘함", "제안"));

        ReportGenerationResult result = service.generate(REPORT_ID);

        assertThat(result.published()).isTrue();
        assertThat(result.textSucceeded()).isEqualTo(1);
        verify(textWriter).writeMemberText(eq(REPORT_ID), eq(1L), any());
        verify(textWriter).publish(REPORT_ID);
        verify(textWriter, never()).markFailed(REPORT_ID);
    }

    @Test
    void failsTheReportWhenThereIsNoActiveMember() {
        givenReportWithMembers();

        ReportGenerationResult result = service.generate(REPORT_ID);

        assertThat(result.published()).isFalse();
        assertThat(result.memberCount()).isZero();
        verify(textWriter).markFailed(REPORT_ID);
        verify(llmGateway, never()).generateMemberText(any());
    }

    @Test
    void failsTheReportWhenExternalBatchCollectionFails() {
        givenReportWithMembers(1L, 2L);
        when(externalDataProvider.provide(eq(PROJECT_ID), any(), any()))
                .thenThrow(new IllegalStateException("외부 집계 실패"));

        ReportGenerationResult result = service.generate(REPORT_ID);

        assertThat(result.published()).isFalse();
        assertThat(result.memberCount()).isEqualTo(2);
        verify(textWriter).markFailed(REPORT_ID);
        verify(dataCollector, never()).collect(anyLong(), anyLong(), any(), any(), any(), anyInt());
        verify(textWriter, never()).publish(anyLong());
    }

    @Test
    void failsTheReportWhenExternalBatchOmitsRequestedMember() {
        givenReportWithMembers(1L, 2L);
        when(externalDataProvider.provide(eq(PROJECT_ID), any(), any()))
                .thenReturn(Map.of(1L, com.plog.domain.report.port.ExternalReportData.notConnected()));

        ReportGenerationResult result = service.generate(REPORT_ID);

        assertThat(result.published()).isFalse();
        assertThat(result.memberCount()).isEqualTo(2);
        verify(textWriter).markFailed(REPORT_ID);
        verify(dataCollector, never()).collect(anyLong(), anyLong(), any(), any(), any(), anyInt());
        verify(textWriter, never()).publish(anyLong());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // 이미 발행된 리포트를 다시 생성하면 내려간 내용이 사후에 바뀐다.
    @Test
    void rejectsRegeneratingAnAlreadyPublishedReport() {
        Report report = report();
        report.complete(LocalDateTime.of(2026, 8, 5, 10, 0));
        when(reportRepository.findWithProjectById(REPORT_ID)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.generate(REPORT_ID))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ReportErrorCode.REPORT_ALREADY_RESOLVED);
    }

    @Test
    void rejectsMissingReport() {
        when(reportRepository.findWithProjectById(REPORT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(REPORT_ID))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ReportErrorCode.REPORT_NOT_FOUND);
    }

    // 팀 인사이트 입력에 실명이 섞이면 안 된다 — 점수·한줄평가만 이름 없이 넘어간다.
    @Test
    void feedsTeamInsightWithAnonymousDistributionOnly() {
        givenReportWithMembers(1L, 2L);
        givenCollectionSucceeds();
        when(llmGateway.generateMemberText(any())).thenReturn(generatedText("한 줄"));
        when(llmGateway.generateTeamText(any())).thenReturn(new TeamReportText("잘함", "제안"));

        service.generate(REPORT_ID);

        org.mockito.ArgumentCaptor<TeamLlmInput> captor =
                org.mockito.ArgumentCaptor.forClass(TeamLlmInput.class);
        verify(llmGateway).generateTeamText(captor.capture());
        TeamLlmInput teamInput = captor.getValue();
        assertThat(teamInput.teamSize()).isEqualTo(2);
        assertThat(teamInput.memberFinalScores()).hasSize(2);
        assertThat(teamInput.memberHeadlines()).containsOnly("한 줄");
        assertThat(teamInput.projectType()).isEqualTo(ProjectType.DEVELOP);
    }

    @Test
    void 서버에서_확정한_팀_분석값을_개인_LLM_입력에_전달한다() {
        givenReportWithMembers(1L);
        givenCollectionSucceeds();
        when(teamMetricService.calculateAndApply(REPORT_ID, 1)).thenReturn(Map.of(
                1L, new ReportTeamMetricService.MemberAnalysis(
                        new BigDecimal("84.00"), new BigDecimal("65.00"),
                        CompetencyCategory.COMMUNICATION)));
        when(llmGateway.generateMemberText(any())).thenReturn(generatedText("한 줄"));
        when(llmGateway.generateTeamText(any())).thenReturn(new TeamReportText("잘함", "제안"));

        service.generate(REPORT_ID);

        org.mockito.ArgumentCaptor<MemberLlmInput> captor =
                org.mockito.ArgumentCaptor.forClass(MemberLlmInput.class);
        verify(llmGateway).generateMemberText(captor.capture());
        assertThat(captor.getValue().collaborationStability()).isEqualByComparingTo("84.00");
        assertThat(captor.getValue().vulnerability()).isEqualByComparingTo("65.00");
        assertThat(captor.getValue().vulnerableCompetency())
                .isEqualTo(CompetencyCategory.COMMUNICATION);
    }

    private void givenReportWithMembers(Long... memberIds) {
        when(reportRepository.findWithProjectById(REPORT_ID)).thenReturn(Optional.of(report()));
        when(projectMemberRepository.findAllByProjectIdAndStatusOrderByIdAsc(PROJECT_ID, MemberStatus.ACTIVE))
                .thenReturn(java.util.Arrays.stream(memberIds).map(this::member).toList());
        when(externalDataProvider.provide(eq(PROJECT_ID), any(), any()))
                .thenReturn(java.util.Arrays.stream(memberIds)
                        .collect(java.util.stream.Collectors.toMap(id -> id, id -> com.plog.domain.report.port.ExternalReportData.notConnected())));
    }

    private void givenCollectionSucceeds() {
        when(dataCollector.collect(anyLong(), anyLong(), any(), any(), any(), anyInt(), any()))
                .thenAnswer(invocation -> collected(
                        ((ProjectMember) invocation.getArgument(3)).getId()));
    }

    private ReportMemberDataCollector.CollectedMember collected(Long memberId) {
        return collected(memberId, InternalReportData.empty());
    }

    private ReportMemberDataCollector.CollectedMember collected(Long memberId, InternalReportData internal) {
        return new ReportMemberDataCollector.CollectedMember(
                memberId,
                MemberLlmInput.of(
                        ProjectType.DEVELOP, 2,
                        internal,
                        com.plog.domain.report.port.ExternalReportData.notConnected(),
                        com.plog.domain.report.port.PeerEvaluationSummary.none(),
                        com.plog.domain.report.port.SelfFeedbackMatchSummary.notSubmitted(),
                        new BigDecimal("82.50")
                ),
                new BigDecimal("82.50"),
                internal
        );
    }

    private InternalReportData internalWithRates() {
        return new InternalReportData(
                List.of(), 1, 1, 1, 1, 1.0, 0.75,
                List.of(), Map.of(), Map.of(), new BigDecimal("80.00"));
    }

    private ReportLlmGateway.GeneratedMemberText generatedText(String headline) {
        return new ReportLlmGateway.GeneratedMemberText(
                new MemberReportText(headline, List.of(), null, null, null),
                "{\"headline\":\"" + headline + "\"}",
                "gemini-2.5-flash"
        );
    }

    private Report report() {
        Report report = Report.start(project());
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        return report;
    }

    private Project project() {
        return Project.builder()
                .id(PROJECT_ID)
                .projectName("Plog")
                .inviteTokenHash("invite-hash")
                .inviteTokenEncrypted("encrypted")
                .projectType(ProjectType.DEVELOP)
                .startDay(LocalDate.of(2026, 5, 1))
                .endDay(LocalDate.of(2026, 6, 12))
                .build();
    }

    private ProjectMember member(Long id) {
        return ProjectMember.builder()
                .id(id)
                .role(ProjectRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .build();
    }
}
