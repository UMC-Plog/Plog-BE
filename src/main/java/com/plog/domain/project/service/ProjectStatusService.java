package com.plog.domain.project.service;

import com.plog.domain.integration.service.IntegrationActorMappingStatusService;
import com.plog.domain.evaluation.repository.PeerEvaluationRepository;
import com.plog.domain.evaluation.repository.SelfFeedbackRepository;
import com.plog.domain.project.dto.ProjectStatusDto;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.repository.ReportRepository;
import com.plog.domain.report.service.ReportLifecycleService;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.api.error.EvaluationErrorCode;
import com.plog.global.util.TimeUtil;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
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
    private final IntegrationActorMappingStatusService actorMappingStatusService;
    private final ReportLifecycleService reportLifecycleService;
    private final ReportRepository reportRepository;

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

        if (!project.isEvaluatingState(TimeUtil.today())) {
            throw new ApiException(EvaluationErrorCode.NOT_EVALUATING_STATE);
        }

        long activeMemberCount = projectMemberRepository.countByProjectIdAndStatus(projectId, MemberStatus.ACTIVE);
        boolean allEvaluationsSubmitted = isAllEvaluationSubmitted(projectId, activeMemberCount);
        if (allEvaluationsSubmitted
                && !actorMappingStatusService.areAllActiveMemberMappingsCompleted(projectId, activeMemberCount)) {
            throw new ApiException(ProjectErrorCode.ACTOR_MAPPING_REQUIRED);
        }
        boolean allSubmitted = allEvaluationsSubmitted;
        boolean timeoutApplied = !allSubmitted && project.isEvaluationClosed(TimeUtil.today());

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
     * 마지막 Peer 평가 또는 자기 피드백 제출 후 전원 제출 조건을 만족하면 평가를 닫는다.
     * 원 제출 트랜잭션과 분리해 자동 완료 실패가 제출 자체를 롤백하지 않도록 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Long> completeAndStartReportIfAllEvaluationsSubmitted(Long projectId) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ApiException(ProjectErrorCode.PROJECT_ACCESS_DENIED_OR_NOT_FOUND));
        if (project.isCompleted()) {
            return Optional.empty();
        }

        long activeMemberCount = projectMemberRepository.countByProjectIdAndStatus(projectId, MemberStatus.ACTIVE);
        if (!isAllEvaluationSubmitted(projectId, activeMemberCount)
                || !actorMappingStatusService.areAllActiveMemberMappingsCompleted(projectId, activeMemberCount)) {
            return Optional.empty();
        }

        project.complete();
        projectRepository.saveAndFlush(project);
        return startReport(project).map(Report::getId);
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
        // 자기 피드백은 선택 사항이므로, 팀원 간 Peer 평가가 모두 제출되면 완료로 본다.
        // 다만 팀원이 없는 solo 프로젝트는 Peer 평가가 애초에 불가능하므로 즉시 완료되지 않도록
        // 자기 피드백 제출을 완료 기준으로 삼는다(미제출 시에는 평가 마감 timeout으로 닫힌다).
        if (activeMemberCount == 1L) {
            return selfFeedbackRepository.countSubmittedByActiveProjectMembers(projectId) >= 1L;
        }
        return true;
    }

    private Optional<Report> startReport(Project project) {
        return reportLifecycleService.startFor(project);
    }

    private ProjectStatusDto.Response toResponse(Project project, boolean timeoutApplied) {
        Report report = reportRepository.findByProjectId(project.getId()).orElse(null);
        return new ProjectStatusDto.Response(
                project.getId(),
                project.getStatus(),
                timeoutApplied,
                project.isCompleted(),
                report == null ? null : report.getId(),
                report == null ? null : report.getStatus()
        );
    }
}
