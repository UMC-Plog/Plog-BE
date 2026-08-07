package com.plog.domain.notification.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.plog.domain.notification.event.PeerEvaluationStartedEvent;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.repository.ProjectRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class PeerEvaluationNotificationSchedulerTest {

    @Test
    void 종료일에_도달하고_아직_알림이_없는_프로젝트의_평가_시작_이벤트를_발행한다() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        Project project = mock(Project.class);
        given(project.getId()).willReturn(10L);
        given(projectRepository.findProjectsAwaitingPeerEvaluationNotification(any(), any(), any()))
                .willReturn(List.of(project));
        PeerEvaluationNotificationScheduler scheduler =
                new PeerEvaluationNotificationScheduler(projectRepository, eventPublisher);

        scheduler.publishDueEvents();

        verify(eventPublisher).publishEvent(new PeerEvaluationStartedEvent(10L, null));
    }
}
