package com.plog.domain.notification.scheduler;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectDeadlineService;
import java.util.List;
import org.junit.jupiter.api.Test;

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
}
