package com.plog.domain.notification.scheduler;

import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectDeadlineService;
import com.plog.global.util.TimeUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PeerEvaluationNotificationScheduler {
    private static final int BATCH_SIZE = 100;

    private final ProjectRepository projectRepository;
    private final ProjectDeadlineService projectDeadlineService;

    @Scheduled(cron = "${plog.notification.peer-evaluation.cron:0 0 0 * * *}")
    public void processDueProjects() {
        List<Long> projectIds = projectRepository.findProjectsAwaitingDeadlineProcessing(
                TimeUtil.today(), PageRequest.of(0, BATCH_SIZE));
        processDeadlineForProjects(projectIds);
        if (!projectIds.isEmpty()) {
            log.info("project_deadline_processing_requested count={}", projectIds.size());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void processDueProjectsOnStartup() {
        try {
            Long lastProjectId = 0L;
            while (true) {
                List<Long> projectIds = projectRepository.findProjectsAwaitingDeadlineProcessingAfterId(
                        TimeUtil.today(), lastProjectId, PageRequest.of(0, BATCH_SIZE));
                if (projectIds.isEmpty()) {
                    break;
                }

                processDeadlineForProjects(projectIds);
                lastProjectId = projectIds.get(projectIds.size() - 1);
                log.info("project_deadline_startup_processing_requested count={} lastProjectId={}",
                        projectIds.size(), lastProjectId);
            }
        } catch (RuntimeException exception) {
            log.warn("project_deadline_startup_catchup_failed", exception);
        }
    }

    private void processDeadlineForProjects(List<Long> projectIds) {
        projectIds.forEach(projectId -> {
            try {
                projectDeadlineService.processDeadline(projectId);
            } catch (RuntimeException exception) {
                log.warn("project_deadline_processing_failed projectId={}", projectId, exception);
            }
        });
    }
}
