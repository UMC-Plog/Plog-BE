package com.plog.domain.project.service;

import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.IntegrationActivityRepository;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.evaluation.repository.PeerEvaluationRepository;
import com.plog.domain.evaluation.repository.SelfFeedbackRepository;
import com.plog.domain.project.dto.ProjectStatusDto;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.report.event.ReportGenerationRequestedEvent;
import com.plog.domain.report.service.ReportLifecycleService;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.util.TimeUtil;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectStatusService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PeerEvaluationRepository peerEvaluationRepository;
    private final SelfFeedbackRepository selfFeedbackRepository;
    private final ProjectAccessService projectAccessService;
    private final ProjectIntegrationRepository projectIntegrationRepository;
    private final IntegrationActivityRepository integrationActivityRepository;
    private final ReportLifecycleService reportLifecycleService;
    private final ApplicationEventPublisher eventPublisher;

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
        if (allEvaluationsSubmitted && hasUnmappedActivityActors(projectId)) {
            throw new ApiException(ProjectErrorCode.ACTOR_MAPPING_REQUIRED);
        }
        boolean allSubmitted = allEvaluationsSubmitted;
        boolean timeoutApplied = !allSubmitted && project.isEvaluationClosed(TimeUtil.todayUtc());

        if (allSubmitted || timeoutApplied) {
            project.complete();
            projectRepository.saveAndFlush(project);
            // 평가가 닫히는 유일한 지점이라 리포트도 여기서 시작한다. 같은 트랜잭션이므로
            // "완료됐는데 리포트가 없는 프로젝트"가 생기지 않는다. 재호출은 멱등이다.
            startReport(project);
        }

        return toResponse(project, timeoutApplied && project.isCompleted());
    }

    /**
     * 평가 또는 자기 피드백 제출 커밋 후 호출되는 자동 완료 경로다.
     * 원 제출 트랜잭션과 분리해 상태 후처리 실패가 제출 자체를 롤백하지 않도록 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAndComplete(Long projectId) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ApiException(ProjectErrorCode.PROJECT_ACCESS_DENIED_OR_NOT_FOUND));
        if (project.isCompleted()) {
            return;
        }

        long activeMemberCount = projectMemberRepository.countByProjectIdAndStatus(projectId, MemberStatus.ACTIVE);
        if (!isAllEvaluationSubmitted(projectId, activeMemberCount) || hasUnmappedActivityActors(projectId)) {
            return;
        }

        project.complete();
        projectRepository.saveAndFlush(project);
        startReport(project);
    }

    private void validateRequestedStatus(ProjectStatusDto.Request request) {
        if (request == null || request.status() == null || request.status() == ProjectStatus.COMPLETED) {
            return;
        }
        throw new ApiException(ProjectErrorCode.INVALID_PROJECT_STATUS_TRANSITION);
    }

    private boolean isAllEvaluationSubmitted(Long projectId, long activeMemberCount) {
        if (activeMemberCount < 1L) {
            return false;
        }
        long requiredPeerEvaluationCount = activeMemberCount * (activeMemberCount - 1L);
        long submittedPeerEvaluationCount = peerEvaluationRepository.countSubmittedByActiveProjectMembers(projectId);
        if (submittedPeerEvaluationCount < requiredPeerEvaluationCount) {
            return false;
        }
        long submittedSelfFeedbackCount = selfFeedbackRepository.countSubmittedByActiveProjectMembers(projectId);

        return submittedSelfFeedbackCount >= activeMemberCount;
    }

    private void startReport(Project project) {
        reportLifecycleService.startFor(project)
                .ifPresent(report -> eventPublisher.publishEvent(
                        new ReportGenerationRequestedEvent(report.getId())));
    }

    private boolean hasUnmappedActivityActors(Long projectId) {
        return projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(projectId).stream()
                .filter(ProjectIntegration::isConnected)
                .anyMatch(integration -> integrationActivityRepository
                        .existsUnassignedActivityActorByProjectIntegrationId(integration.getId()));
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
