package com.plog.domain.project.service;

import com.plog.domain.integration.entity.IntegrationCollectionJobStatus;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.integration.service.IntegrationCollectionJobService;
import com.plog.domain.notification.event.PeerEvaluationStartedEvent;
import com.plog.domain.project.entity.PeerEvaluationStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectCollectionStatus;
import com.plog.domain.project.event.InternalActivityCollectionRequestedEvent;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectDeadlineService {

    private final ProjectRepository projectRepository;
    private final ProjectIntegrationRepository projectIntegrationRepository;
    private final IntegrationCollectionJobService integrationCollectionJobService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void processDeadline(Long projectId) {
        Project project = projectRepository.findByIdForUpdate(projectId).orElse(null);
        if (project == null || project.isCompleted() || TimeUtil.today().isBefore(project.getEndDay())) {
            return;
        }

        ProjectCollectionStatus internalStatus = project.getInternalCollectionStatus();
        if (internalStatus == ProjectCollectionStatus.NOT_STARTED
                || internalStatus == ProjectCollectionStatus.FAILED) {
            project.queueInternalCollection();
            eventPublisher.publishEvent(new InternalActivityCollectionRequestedEvent(projectId));
        }

        if (project.getPeerEvaluationStatus() != PeerEvaluationStatus.PENDING) {
            return;
        }

        if (!projectIntegrationRepository.hasConnectedIntegration(projectId)) {
            project.skipExternalCollection();
            openEvaluation(project);
            return;
        }

        project.queueExternalCollection();
        integrationCollectionJobService.enqueue(projectId, null);
    }

    @Transactional
    public void completeExternalCollection(Long projectId, IntegrationCollectionJobStatus status) {
        Project project = projectRepository.findByIdForUpdate(projectId).orElse(null);
        if (project == null || project.isCompleted()
                || TimeUtil.today().isBefore(project.getEndDay())) {
            return;
        }
        ProjectCollectionStatus terminalStatus = switch (status) {
            case SUCCEEDED -> ProjectCollectionStatus.SUCCEEDED;
            case PARTIAL_FAILED -> ProjectCollectionStatus.PARTIAL_FAILED;
            case FAILED -> ProjectCollectionStatus.FAILED;
            default -> throw new IllegalArgumentException("external collection is not finished: " + status);
        };
        project.finishExternalCollection(terminalStatus);
        openEvaluation(project);
    }

    @Transactional
    public void markExternalCollectionRunning(Long projectId) {
        projectRepository.findByIdForUpdate(projectId).ifPresent(Project::startExternalCollection);
    }

    @Transactional
    public void markInternalCollectionRunning(Long projectId) {
        projectRepository.findByIdForUpdate(projectId).ifPresent(Project::startInternalCollection);
    }

    @Transactional
    public void finishInternalCollection(Long projectId, boolean succeeded) {
        projectRepository.findByIdForUpdate(projectId)
                .ifPresent(project -> project.finishInternalCollection(succeeded));
    }

    private void openEvaluation(Project project) {
        if (project.openPeerEvaluation()) {
            eventPublisher.publishEvent(new PeerEvaluationStartedEvent(project.getId(), null));
        }
    }
}
