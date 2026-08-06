package com.plog.domain.project.service;

import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.IntegrationActivityRepository;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.evaluation.repository.PeerEvaluationRepository;
import com.plog.domain.project.dto.ProjectStatusDto;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.report.service.ReportLifecycleService;
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

    private static final long MIN_MEMBERS_FOR_PEER_EVALUATION = 2L;

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PeerEvaluationRepository peerEvaluationRepository;
    private final ProjectAccessService projectAccessService;
    private final ProjectIntegrationRepository projectIntegrationRepository;
    private final IntegrationActivityRepository integrationActivityRepository;
    private final ReportLifecycleService reportLifecycleService;

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
            reportLifecycleService.startFor(project);
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
        // 활성 멤버가 1명이면 평가할 상대가 없어 필요 건수가 0이 된다.
        // 이걸 "전원 제출"로 보면 아무 입력 없이 리포트가 발행되므로, 타임아웃 경로에만 맡긴다.
        if (activeMemberCount < MIN_MEMBERS_FOR_PEER_EVALUATION) {
            return false;
        }
        long requiredPeerEvaluationCount = activeMemberCount * (activeMemberCount - 1L);
        long submittedPeerEvaluationCount = peerEvaluationRepository.countSubmittedByActiveProjectMembers(projectId);

        return submittedPeerEvaluationCount >= requiredPeerEvaluationCount;
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
