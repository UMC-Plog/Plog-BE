package com.plog.domain.project.service;

import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.integration.repository.ProjectMemberIntegrationIdentityRepository;
import com.plog.domain.evaluation.repository.PeerEvaluationRepository;
import com.plog.domain.evaluation.repository.SelfFeedbackRepository;
import com.plog.domain.project.dto.ProjectStatusDto;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.util.TimeUtil;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectStatusService {

    private static final long TIMEOUT_DAYS_AFTER_END = 7L;

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PeerEvaluationRepository peerEvaluationRepository;
    private final SelfFeedbackRepository selfFeedbackRepository;
    private final ProjectAccessService projectAccessService;
    private final ProjectIntegrationRepository projectIntegrationRepository;
    private final ProjectMemberIntegrationIdentityRepository identityRepository;

    @Transactional
    public ProjectStatusDto.Response checkAndUpdateStatus(
            Long projectId,
            Long userId,
            ProjectStatusDto.Request request
    ) {
        projectAccessService.requireActiveMember(projectId, userId);

        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ApiException(ProjectErrorCode.PROJECT_ACCESS_DENIED_OR_NOT_FOUND));

        validateRequestedStatus(request);

        if (project.isCompleted()) {
            return toResponse(project, false);
        }

        long activeMemberCount = projectMemberRepository.countByProjectIdAndStatus(projectId, MemberStatus.ACTIVE);
        boolean allEvaluationsSubmitted = isAllEvaluationSubmitted(projectId, activeMemberCount);
        if (allEvaluationsSubmitted && !hasAllActorMappings(projectId, activeMemberCount)) {
            throw new ApiException(ProjectErrorCode.ACTOR_MAPPING_REQUIRED);
        }
        boolean allSubmitted = allEvaluationsSubmitted;
        boolean timeoutApplied = !allSubmitted && isTimeoutReached(project.getEndDay());

        if (allSubmitted || timeoutApplied) {
            project.complete();
            projectRepository.saveAndFlush(project);
        }

        return toResponse(project, timeoutApplied && project.isCompleted());
    }

    private void validateRequestedStatus(ProjectStatusDto.Request request) {
        if (request == null || request.status() == null || request.status() == ProjectStatus.COMPLETED) {
            return;
        }
        throw new ApiException(ProjectErrorCode.INVALID_PROJECT_STATUS_TRANSITION);
    }

    private boolean isAllEvaluationSubmitted(Long projectId, long activeMemberCount) {
        long requiredPeerEvaluationCount = activeMemberCount * Math.max(activeMemberCount - 1L, 0L);
        long submittedPeerEvaluationCount = peerEvaluationRepository.countSubmittedByActiveProjectMembers(projectId);
        long submittedSelfFeedbackCount = selfFeedbackRepository.countSubmittedByActiveProjectMembers(projectId);

        return submittedPeerEvaluationCount >= requiredPeerEvaluationCount
                && submittedSelfFeedbackCount >= activeMemberCount;
    }

    private boolean hasAllActorMappings(Long projectId, long activeMemberCount) {
        return projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(projectId).stream()
                .filter(ProjectIntegration::isConnected)
                .allMatch(integration -> identityRepository
                        .countByProjectIntegrationIdAndProjectMemberStatus(
                                integration.getId(), MemberStatus.ACTIVE) >= activeMemberCount);
    }

    private boolean isTimeoutReached(LocalDate endDay) {
        LocalDate today = TimeUtil.todayUtc();
        return !today.isBefore(endDay.plusDays(TIMEOUT_DAYS_AFTER_END));
    }

    private ProjectStatusDto.Response toResponse(Project project, boolean timeoutApplied) {
        return new ProjectStatusDto.Response(
                project.getId(),
                project.getStatus(),
                timeoutApplied,
                project.isCompleted()
        );
    }
}
