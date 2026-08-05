package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.project.entity.Project;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.ReportStatus;
import com.plog.domain.report.repository.ReportRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReportBatchServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ReportLifecycleService reportLifecycleService;

    @Mock
    private ReportGenerationService reportGenerationService;

    @InjectMocks
    private ReportBatchService reportBatchService;

    // 행만 만들고 끝내면 GENERATING 으로 남아 다음 회차 대상에서도 빠진다 — 영영 발행되지 않는다.
    @Test
    void generatesTheReportRightAfterStartingIt() {
        givenDueProjects(1L);
        Report started = mockReport();
        ReflectionTestUtils.setField(started, "id", 77L);
        when(reportLifecycleService.closeEvaluationAndStart(1L)).thenReturn(Optional.of(started));

        reportBatchService.startDueReports();

        verify(reportGenerationService).generate(77L);
    }

    @Test
    void doesNotGenerateForSkippedProjects() {
        givenDueProjects(1L);
        when(reportLifecycleService.closeEvaluationAndStart(1L)).thenReturn(Optional.empty());

        reportBatchService.startDueReports();

        verify(reportGenerationService, never()).generate(anyLong());
    }

    @Test
    void startsReportsForEveryDueProject() {
        givenDueProjects(1L, 2L);
        when(reportLifecycleService.closeEvaluationAndStart(anyLong()))
                .thenReturn(Optional.of(mockReport()));

        ReportBatchResult result = reportBatchService.startDueReports();

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.started()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        verify(reportLifecycleService).closeEvaluationAndStart(1L);
        verify(reportLifecycleService).closeEvaluationAndStart(2L);
    }

    // B의 핵심 요구사항 — 프로젝트 1건이 터져도 나머지는 끝까지 처리되어야 한다.
    @Test
    void isolatesFailuresSoOneBrokenProjectDoesNotStopTheBatch() {
        givenDueProjects(1L, 2L, 3L);
        when(reportLifecycleService.closeEvaluationAndStart(1L)).thenReturn(Optional.of(mockReport()));
        when(reportLifecycleService.closeEvaluationAndStart(2L))
                .thenThrow(new IllegalStateException("boom"));
        when(reportLifecycleService.closeEvaluationAndStart(3L)).thenReturn(Optional.of(mockReport()));

        ReportBatchResult result = reportBatchService.startDueReports();

        assertThat(result.scanned()).isEqualTo(3);
        assertThat(result.started()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        // 실패 뒤에 온 프로젝트도 처리됐는지 — 여기가 깨지면 배치가 중간에 죽은 것이다.
        verify(reportLifecycleService).closeEvaluationAndStart(3L);
    }

    @Test
    void countsAlreadyReportedProjectsAsSkipped() {
        givenDueProjects(1L, 2L);
        when(reportLifecycleService.closeEvaluationAndStart(1L)).thenReturn(Optional.of(mockReport()));
        when(reportLifecycleService.closeEvaluationAndStart(2L)).thenReturn(Optional.empty());

        ReportBatchResult result = reportBatchService.startDueReports();

        assertThat(result.started()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failed()).isZero();
    }

    @Test
    void doesNothingWhenNoProjectIsDue() {
        when(reportRepository.findProjectsDueForReport(any(), any(), any())).thenReturn(List.of());

        ReportBatchResult result = reportBatchService.startDueReports();

        assertThat(result.hasWork()).isFalse();
        verify(reportLifecycleService, never()).closeEvaluationAndStart(anyLong());
    }

    /**
     * 유예 기간을 배치가 따로 계산하면 엔티티 규칙과 어긋난다.
     * 조회 상한이 {@code Project.latestEndDayWithClosedEvaluation(오늘)} 과 같은지 고정한다.
     */
    @Test
    void queriesWithTheEvaluationDeadlineDerivedFromTheProjectEntity() {
        when(reportRepository.findProjectsDueForReport(any(), any(), any())).thenReturn(List.of());

        reportBatchService.startDueReports();

        ArgumentCaptor<LocalDate> bound = ArgumentCaptor.forClass(LocalDate.class);
        verify(reportRepository).findProjectsDueForReport(
                bound.capture(),
                eq(ReportStatus.restartBlockingStatuses()),
                any(Pageable.class)
        );
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        assertThat(bound.getValue()).isEqualTo(Project.latestEndDayWithClosedEvaluation(today));
        // 마감 당일(유예 0일)은 아직 대상이 아니다 — 상한이 오늘보다 과거여야 한다.
        assertThat(bound.getValue()).isBefore(today);
    }

    private void givenDueProjects(Long... projectIds) {
        List<Project> projects = java.util.Arrays.stream(projectIds)
                .map(this::project)
                .toList();
        when(reportRepository.findProjectsDueForReport(any(), any(), any())).thenReturn(projects);
    }

    private Project project(Long id) {
        return Project.builder()
                .id(id)
                .projectName("Plog-" + id)
                .inviteTokenHash("invite-hash-" + id)
                .inviteTokenEncrypted("encrypted-" + id)
                .startDay(LocalDate.of(2026, 5, 1))
                .endDay(LocalDate.of(2026, 6, 12))
                .build();
    }

    private Report mockReport() {
        return Report.start(project(99L));
    }
}
