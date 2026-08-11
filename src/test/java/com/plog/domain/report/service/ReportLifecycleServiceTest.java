package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.ReportStatus;
import com.plog.domain.report.event.ReportGenerationRequestedEvent;
import com.plog.domain.report.repository.ReportRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportLifecycleServiceTest {

    private static final Long PROJECT_ID = 1L;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ReportLifecycleService reportLifecycleService;

    @Test
    void startsGeneratingReportWhenProjectHasNone() {
        Project project = project();
        when(reportRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            ReflectionTestUtils.setField(report, "id", 10L);
            return report;
        });

        Optional<Report> started = reportLifecycleService.startFor(project);

        assertThat(started).isPresent();
        ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ReportStatus.GENERATING);
        assertThat(saved.getValue().getProject()).isSameAs(project);
        verify(eventPublisher).publishEvent(new ReportGenerationRequestedEvent(10L));
    }

    @Test
    void skipsGeneratingReport() {
        Report generating = Report.start(project());
        when(reportRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(generating));

        Optional<Report> started = reportLifecycleService.startFor(project());

        assertThat(started).isEmpty();
        verify(reportRepository, never()).save(any(Report.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void skipsCompletedReport() {
        Report completed = Report.start(project());
        completed.complete(java.time.LocalDateTime.of(2026, 8, 5, 10, 0));
        when(reportRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(completed));

        assertThat(reportLifecycleService.startFor(project())).isEmpty();
        verify(reportRepository, never()).save(any(Report.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void reusesFailedReportAndRequestsGeneration() {
        Report failed = Report.start(project());
        ReflectionTestUtils.setField(failed, "id", 20L);
        failed.fail();
        when(reportRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(failed));

        Optional<Report> restarted = reportLifecycleService.startFor(project());

        assertThat(restarted).containsSame(failed);
        assertThat(failed.getStatus()).isEqualTo(ReportStatus.GENERATING);
        verify(reportRepository, never()).save(any(Report.class));
        verify(eventPublisher).publishEvent(new ReportGenerationRequestedEvent(20L));
    }

    // 평가를 닫지 않으면 리포트 발행 후에도 동료 평가가 계속 들어와 근거 데이터가 나중에 바뀐다.
    @Test
    void closesEvaluationBeforeStartingTheReport() {
        Project project = project();
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(reportRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Report> started = reportLifecycleService.closeEvaluationAndStart(PROJECT_ID);

        assertThat(started).isPresent();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.COMPLETED);
        assertThat(project.isEvaluatingState(java.time.LocalDate.of(2026, 8, 5))).isFalse();
    }

    // 사용자 경로가 이미 완료시킨 프로젝트도 리포트만 없으면 배치가 채워야 한다.
    @Test
    void startsReportForAnAlreadyCompletedProject() {
        Project project = project();
        project.complete();
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(reportRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(reportLifecycleService.closeEvaluationAndStart(PROJECT_ID)).isPresent();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.COMPLETED);
    }

    @Test
    void skipsWhenTheDueProjectAlreadyHasAReport() {
        Project project = project();
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(reportRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(Report.start(project)));

        assertThat(reportLifecycleService.closeEvaluationAndStart(PROJECT_ID)).isEmpty();
        verify(reportRepository, never()).save(any(Report.class));
    }

    @Test
    void rejectsUnsavedProject() {
        assertThatThrownBy(() -> reportLifecycleService.startFor(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reportLifecycleService.startFor(Project.builder()
                        .projectName("Plog")
                        .inviteTokenHash("invite-hash")
                        .inviteTokenEncrypted("encrypted-invite")
                        .build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Project project() {
        return Project.builder()
                .id(PROJECT_ID)
                .projectName("Plog")
                .inviteTokenHash("invite-hash")
                .inviteTokenEncrypted("encrypted-invite")
                .build();
    }
}
