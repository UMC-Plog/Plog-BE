package com.plog.domain.notification.scheduler;

import com.plog.domain.notification.entity.NotificationType;
import com.plog.domain.notification.event.PeerEvaluationStartedEvent;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.util.TimeUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PeerEvaluationNotificationScheduler {
    private static final int BATCH_SIZE = 100;

    private final ProjectRepository projectRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(cron = "${plog.notification.peer-evaluation.cron:0 0 0 * * *}")
    @Transactional(readOnly = true)
    public void publishDueEvents() {
        List<Project> projects = projectRepository.findProjectsAwaitingPeerEvaluationNotification(
                TimeUtil.todayUtc(), NotificationType.PEER_EVALUATION_STARTED, PageRequest.of(0, BATCH_SIZE));
        projects.forEach(project -> eventPublisher.publishEvent(
                new PeerEvaluationStartedEvent(project.getId(), null)));
        if (!projects.isEmpty()) {
            log.info("peer_evaluation_notification_events_published count={}", projects.size());
        }
    }
}
