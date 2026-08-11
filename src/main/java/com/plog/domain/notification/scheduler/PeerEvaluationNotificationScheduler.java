package com.plog.domain.notification.scheduler;

import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectDeadlineService;
import com.plog.global.util.TimeUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
                TimeUtil.todayUtc(), PageRequest.of(0, BATCH_SIZE));
        projectIds.forEach(projectDeadlineService::processDeadline);
        if (!projectIds.isEmpty()) {
            log.info("project_deadline_processing_requested count={}", projectIds.size());
        }
    }
}
