package com.plog.domain.project.service;

import com.plog.domain.evaluation.repository.PeerEvaluationRepository;
import com.plog.domain.project.dto.ProjectStatusDto;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectStatusService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PeerEvaluationRepository peerEvaluationRepository;
    private final ProjectAccessService projectAccessService;
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
            return toResponse(project, userId, false);
        }

        if (!project.isEvaluatingState(TimeUtil.today())) {
            throw new ApiException(EvaluationErrorCode.NOT_EVALUATING_STATE);
        }

        long activeMemberCount = projectMemberRepository.countByProjectIdAndStatus(projectId, MemberStatus.ACTIVE);
        ProjectMember currentMember = projectMemberRepository.findByProjectIdAndUserIdAndStatusForUpdate(
                        projectId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ProjectErrorCode.PROJECT_MEMBER_REQUIRED));
        boolean currentMemberPeerEvaluationCompleted = currentMember.isFinalSubmitted()
                || isMemberPeerEvaluationCompleted(projectId, currentMember.getId(), activeMemberCount);
        boolean timeoutApplied = project.isEvaluationClosed(TimeUtil.today());

        if (!timeoutApplied && !currentMember.isFinalSubmitted() && !currentMemberPeerEvaluationCompleted) {
            throw new ApiException(EvaluationErrorCode.PEER_EVALUATION_REQUIRED_FOR_FINAL_SUBMISSION);
        }

        if (!timeoutApplied) {
            currentMember.submitFinal(TimeUtil.now());
        }

        if (timeoutApplied || isAllFinalSubmitted(projectId, activeMemberCount)) {
            project.complete();
            projectRepository.saveAndFlush(project);
            // 평가가 닫히는 유일한 지점이라 리포트도 여기서 시작한다. 같은 트랜잭션이므로
            // "완료됐는데 리포트가 없는 프로젝트"가 생기지 않는다. 재호출은 멱등이다.
            startReport(project);
        }

        return toResponse(project, userId, timeoutApplied && project.isCompleted());
    }

    private void validateRequestedStatus(ProjectStatusDto.Request request) {
        if (request == null || request.status() == null || request.status() == ProjectStatus.COMPLETED) {
            return;
        }
        throw new ApiException(ProjectErrorCode.INVALID_PROJECT_STATUS_TRANSITION);
    }

    private boolean isMemberPeerEvaluationCompleted(Long projectId, Long evaluatorId, long activeMemberCount) {
        if (activeMemberCount < 1L) {
            return false;
        }
        long requiredPeerEvaluationCount = activeMemberCount - 1L;
        long submittedPeerEvaluationCount = peerEvaluationRepository
                .countSubmittedByActiveEvaluator(projectId, evaluatorId);
        return submittedPeerEvaluationCount >= requiredPeerEvaluationCount;
    }

    private boolean isAllFinalSubmitted(Long projectId, long activeMemberCount) {
        if (activeMemberCount < 1L) {
            return false;
        }
        return projectMemberRepository.countByProjectIdAndStatusAndFinalSubmittedAtIsNotNull(
                projectId, MemberStatus.ACTIVE) >= activeMemberCount;
    }

    private void startReport(Project project) {
        reportLifecycleService.startFor(project);
    }

    private ProjectStatusDto.Response toResponse(Project project, Long userId, boolean timeoutApplied) {
        ProjectMember currentMember = projectMemberRepository.findByProjectIdAndUserId(project.getId(), userId)
                .orElse(null);
        long totalFinalSubmissionCount = projectMemberRepository.countByProjectIdAndStatus(
                project.getId(), MemberStatus.ACTIVE);
        long completedFinalSubmissionCount = projectMemberRepository
                .countByProjectIdAndStatusAndFinalSubmittedAtIsNotNull(project.getId(), MemberStatus.ACTIVE);
        Report report = reportRepository.findByProjectId(project.getId()).orElse(null);
        return new ProjectStatusDto.Response(
                project.getId(),
                project.getStatus(),
                timeoutApplied,
                project.isCompleted(),
                currentMember != null && currentMember.isFinalSubmitted(),
                Math.toIntExact(completedFinalSubmissionCount),
                Math.toIntExact(totalFinalSubmissionCount),
                report == null ? null : report.getId(),
                report == null ? null : report.getStatus()
        );
    }
}
