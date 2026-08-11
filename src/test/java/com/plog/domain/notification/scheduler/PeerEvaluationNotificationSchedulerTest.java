package com.plog.domain.notification.scheduler;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectDeadlineService;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class PeerEvaluationNotificationSchedulerTest {

    @Test
    void 종료일에_도달하고_아직_알림이_없는_프로젝트의_평가_시작_이벤트를_발행한다() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectDeadlineService deadlineService = mock(ProjectDeadlineService.class);
        given(projectRepository.findProjectsAwaitingDeadlineProcessing(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of(10L));
        PeerEvaluationNotificationScheduler scheduler =
                new PeerEvaluationNotificationScheduler(projectRepository, deadlineService);

        scheduler.processDueProjects();

        verify(deadlineService).processDeadline(10L);
    }

    @Test
    void 서버_기동_시_미처리_프로젝트를_한번_복구_처리한다() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectDeadlineService deadlineService = mock(ProjectDeadlineService.class);
        given(projectRepository.findProjectsAwaitingDeadlineProcessingAfterId(
                ArgumentMatchers.any(), ArgumentMatchers.eq(0L), ArgumentMatchers.any()))
                .willReturn(List.of(10L));
        given(projectRepository.findProjectsAwaitingDeadlineProcessingAfterId(
                ArgumentMatchers.any(), ArgumentMatchers.eq(10L), ArgumentMatchers.any()))
                .willReturn(List.of());
        PeerEvaluationNotificationScheduler scheduler =
                new PeerEvaluationNotificationScheduler(projectRepository, deadlineService);

        scheduler.processDueProjectsOnStartup();

        verify(deadlineService).processDeadline(10L);
    }

    @Test
    void 서버_기동_중_예외가_나도_애플리케이션_기동은_막지_않는다() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectDeadlineService deadlineService = mock(ProjectDeadlineService.class);
        given(projectRepository.findProjectsAwaitingDeadlineProcessingAfterId(
                ArgumentMatchers.any(), ArgumentMatchers.anyLong(), ArgumentMatchers.any()))
                .willThrow(new RuntimeException("boom"));
        PeerEvaluationNotificationScheduler scheduler =
                new PeerEvaluationNotificationScheduler(projectRepository, deadlineService);

        Assertions.assertDoesNotThrow(scheduler::processDueProjectsOnStartup);

        verify(deadlineService, never()).processDeadline(ArgumentMatchers.anyLong());
    }

    @Test
    void 서버_기동_시_배치_크기를_초과한_미처리_프로젝트를_커서로_모두_복구한다() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectDeadlineService deadlineService = mock(ProjectDeadlineService.class);
        List<Long> firstBatch = LongStream.rangeClosed(1L, 100L).boxed().toList();
        List<Long> secondBatch = LongStream.rangeClosed(101L, 105L).boxed().toList();
        given(projectRepository.findProjectsAwaitingDeadlineProcessingAfterId(
                ArgumentMatchers.any(), ArgumentMatchers.eq(0L), ArgumentMatchers.any()))
                .willReturn(firstBatch);
        given(projectRepository.findProjectsAwaitingDeadlineProcessingAfterId(
                ArgumentMatchers.any(), ArgumentMatchers.eq(100L), ArgumentMatchers.any()))
                .willReturn(secondBatch);
        given(projectRepository.findProjectsAwaitingDeadlineProcessingAfterId(
                ArgumentMatchers.any(), ArgumentMatchers.eq(105L), ArgumentMatchers.any()))
                .willReturn(List.of());
        PeerEvaluationNotificationScheduler scheduler =
                new PeerEvaluationNotificationScheduler(projectRepository, deadlineService);

        scheduler.processDueProjectsOnStartup();

        for (Long projectId : LongStream.rangeClosed(1L, 105L).boxed().toList()) {
            verify(deadlineService).processDeadline(projectId);
        }
    }

    @Test
    void 개별_프로젝트_처리_실패가_다음_프로젝트_처리를_막지_않는다() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectDeadlineService deadlineService = mock(ProjectDeadlineService.class);
        given(projectRepository.findProjectsAwaitingDeadlineProcessing(
                ArgumentMatchers.any(), ArgumentMatchers.any()))
                .willReturn(List.of(1L, 2L));
        doThrow(new RuntimeException("boom")).when(deadlineService).processDeadline(1L);
        PeerEvaluationNotificationScheduler scheduler =
                new PeerEvaluationNotificationScheduler(projectRepository, deadlineService);

        scheduler.processDueProjects();

        verify(deadlineService).processDeadline(1L);
        verify(deadlineService).processDeadline(2L);
    }

    @Test
    void 서버_기동_시_실패한_프로젝트도_한번만_시도하고_커서를_진전한다() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectDeadlineService deadlineService = mock(ProjectDeadlineService.class);
        given(projectRepository.findProjectsAwaitingDeadlineProcessingAfterId(
                ArgumentMatchers.any(), ArgumentMatchers.eq(0L), ArgumentMatchers.any()))
                .willReturn(List.of(1L, 2L));
        given(projectRepository.findProjectsAwaitingDeadlineProcessingAfterId(
                ArgumentMatchers.any(), ArgumentMatchers.eq(2L), ArgumentMatchers.any()))
                .willReturn(List.of());
        doThrow(new RuntimeException("boom")).when(deadlineService).processDeadline(1L);
        PeerEvaluationNotificationScheduler scheduler =
                new PeerEvaluationNotificationScheduler(projectRepository, deadlineService);

        scheduler.processDueProjectsOnStartup();

        verify(deadlineService, times(1)).processDeadline(1L);
        verify(deadlineService).processDeadline(2L);
        verify(projectRepository).findProjectsAwaitingDeadlineProcessingAfterId(
                ArgumentMatchers.any(), ArgumentMatchers.eq(2L), ArgumentMatchers.any());
    }
}
