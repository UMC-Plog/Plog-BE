package com.plog.domain.integration.service;

import com.plog.domain.integration.entity.IntegrationCollectionRun;
import com.plog.domain.integration.entity.IntegrationCollectionRunStatus;
import com.plog.domain.integration.repository.IntegrationCollectionRunRepository;
import com.plog.domain.project.entity.Project;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 프로젝트 완료 전이와 함께 최종 수집 요청을 한 번만 기록한다. 실제 provider 수집은 이 run을 claim해 수행한다. */
@Service
@RequiredArgsConstructor
public class IntegrationCollectionRunService {

    private final IntegrationCollectionRunRepository integrationCollectionRunRepository;

    @Transactional
    public void createPendingFinalRun(Project project) {
        integrationCollectionRunRepository.createIfAbsent(project.getId());
    }

    @Transactional
    public void reclaimStaleRunningRun(Long projectId, Instant now, Instant staleBefore) {
        integrationCollectionRunRepository.findByProjectIdForUpdate(projectId)
                .filter(run -> run.getStatus() == IntegrationCollectionRunStatus.RUNNING)
                .filter(run -> run.getHeartbeatAt() == null || run.getHeartbeatAt().isBefore(staleBefore))
                .ifPresent(run -> run.markRetryable(
                        run.getAttemptToken(), now, "stale collection run reclaimed"
                ));
    }
}
