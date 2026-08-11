package com.plog.domain.evaluation.service;

import com.plog.domain.evaluation.dto.request.PeerEvaluationCreateRequest;
import com.plog.domain.evaluation.dto.response.EvaluationTargetResponse;
import com.plog.domain.evaluation.dto.response.PeerEvaluationCreateResponse;
import com.plog.domain.evaluation.dto.response.PeerEvaluationDetailResponse;
import com.plog.domain.evaluation.dto.response.TargetMemberDto;
import com.plog.domain.evaluation.entity.PeerEvaluation;
import com.plog.domain.evaluation.event.PeerEvaluationSubmittedEvent;
import com.plog.domain.evaluation.repository.PeerEvaluationRepository;
import com.plog.domain.evaluation.repository.SelfFeedbackRepository;
import com.plog.domain.integration.service.IntegrationActorMappingStatusService;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import com.plog.global.api.code.ErrorCode;
import com.plog.global.api.error.EvaluationErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PeerEvaluationRepository peerEvaluationRepository;
    private final SelfFeedbackRepository selfFeedbackRepository;
    private final IntegrationActorMappingStatusService actorMappingStatusService;
    private final EvaluationParticipantResolver participantResolver;
    private final ReportActivityLogRepository reportActivityLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EvaluationTargetResponse getEvaluationTargets(Long projectId, Long userId) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        requireEvaluationOpen(project);

        ProjectMember currentMember = participantResolver.requireEvaluator(projectId, userId);

        List<ProjectMember> allMembers = projectMemberRepository
                .findAllByProjectIdAndStatusOrderByIdAsc(projectId, MemberStatus.ACTIVE);

        Set<Long> evaluatedTargetIds = peerEvaluationRepository.findEvaluatedTargetIds(currentMember);

        List<TargetMemberDto> targets = allMembers.stream()
                .filter(member -> !member.getId().equals(currentMember.getId())) // 본인 제외
                .map(member -> {
                    boolean isEvaluated = evaluatedTargetIds.contains(member.getId());

                    return TargetMemberDto.builder()
                            .projectMemberId(member.getId())
                            .nickname(member.getDisplayNickname())
                            .profilePreset(member.getUser().getProfilePreset())
                            .isEvaluated(isEvaluated)
                            .build();
                })
                .toList();

        int completedPeerEvaluationCount = (int) targets.stream()
                .filter(TargetMemberDto::isEvaluated)
                .count();
        int totalPeerEvaluationCount = targets.size();
        boolean isSelfFeedbackCompleted = selfFeedbackRepository
                .findByProjectMemberId(currentMember.getId())
                .isPresent();
        boolean isAccountMappingCompleted = actorMappingStatusService
                .isMyMappingCompleted(projectId, currentMember.getId());
        boolean isFinalSubmissionAvailable = !project.isCompleted()
                && completedPeerEvaluationCount == totalPeerEvaluationCount
                && isSelfFeedbackCompleted
                && isAccountMappingCompleted;

        return new EvaluationTargetResponse(
                targets,
                completedPeerEvaluationCount,
                totalPeerEvaluationCount,
                isSelfFeedbackCompleted,
                isAccountMappingCompleted,
                isFinalSubmissionAvailable
        );
    }

    public PeerEvaluationDetailResponse getPeerEvaluationDetail(Long projectId, Long targetMemberId, Long userId) {

        ProjectMember evaluator = participantResolver.requireEvaluator(projectId, userId);
        requireEvaluationOpen(evaluator.getProject());
        ProjectMember evaluatee = participantResolver.requireEvaluatee(projectId, targetMemberId);

        PeerEvaluation evaluation = peerEvaluationRepository.findByEvaluatorIdAndEvaluateeId(evaluator.getId(), targetMemberId)
                .orElseThrow(() -> new ApiException(EvaluationErrorCode.EVALUATION_NOT_FOUND));

        return PeerEvaluationDetailResponse.from(evaluation);
    }

    @Transactional
    public PeerEvaluationCreateResponse createPeerEvaluation(
            Long projectId,
            Long targetMemberId,
            Long userId,
            PeerEvaluationCreateRequest request) {

        ProjectMember evaluator = participantResolver.requireEvaluator(projectId, userId);
        ProjectMember evaluatee = participantResolver.requireEvaluatee(projectId, targetMemberId);

        requireEvaluationOpen(evaluatee.getProject());

        if (evaluator.getId().equals(evaluatee.getId())) {
            throw new ApiException(EvaluationErrorCode.CANNOT_EVALUATE_SELF);
        }

        if (peerEvaluationRepository.findByEvaluatorIdAndEvaluateeId(evaluator.getId(), evaluatee.getId()).isPresent()) {
            throw new ApiException(EvaluationErrorCode.ALREADY_EVALUATED);
        }

        PeerEvaluation evaluation = PeerEvaluation.builder()
                .evaluator(evaluator)
                .evaluatee(evaluatee)
                .collaborationScore(request.collaborationScore())
                .initiativeScore(request.initiativeScore())
                .communicationScore(request.communicationScore())
                .outputScore(request.outputScore())
                .keywords(request.keywords())
                .feedback(request.feedback())
                .build();

        peerEvaluationRepository.save(evaluation);
        eventPublisher.publishEvent(new PeerEvaluationSubmittedEvent(
                evaluation.getId(), evaluator.getId(), evaluatee.getId(), TimeUtil.now()));

        return new PeerEvaluationCreateResponse(evaluation.getId(), hasUniformScores(request));
    }

    @Transactional
    public PeerEvaluationCreateResponse updatePeerEvaluation(
            Long projectId,
            Long targetMemberId,
            Long userId,
            PeerEvaluationCreateRequest request
    ) {
        ProjectMember evaluator = participantResolver.requireEvaluator(projectId, userId);
        ProjectMember evaluatee = participantResolver.requireEvaluatee(projectId, targetMemberId);

        if (evaluatee.getProject().isCompleted()) {
            throw new ApiException(EvaluationErrorCode.CANNOT_MODIFY_EVALUATION_AFTER_PUBLISH);
        }
        requireEvaluationOpen(evaluatee.getProject());

        PeerEvaluation evaluation = peerEvaluationRepository
                .findByEvaluatorIdAndEvaluateeId(evaluator.getId(), evaluatee.getId())
                .orElseThrow(() -> new ApiException(EvaluationErrorCode.EVALUATION_NOT_FOUND));

        reportActivityLogRepository.acquireSourceLock(
                SourceDomain.EVALUATION.name() + ":peer-evaluation:" + evaluation.getId());
        evaluation.update(
                request.collaborationScore(),
                request.initiativeScore(),
                request.communicationScore(),
                request.outputScore(),
                request.keywords(),
                request.feedback()
        );

        String sourceRefId = "peer-evaluation:" + evaluation.getId();
        reportActivityLogRepository.refreshSourceSnapshot(
                SourceDomain.EVALUATION.name(), sourceRefId, evaluation.getFeedback(),
                evaluationMetadata(evaluation));
        eventPublisher.publishEvent(new PeerEvaluationSubmittedEvent(
                evaluation.getId(), evaluator.getId(), evaluatee.getId(),
                evaluation.getCreatedAt() != null ? evaluation.getCreatedAt() : TimeUtil.now()));

        return new PeerEvaluationCreateResponse(evaluation.getId(), hasUniformScores(request));
    }

    private String evaluationMetadata(PeerEvaluation evaluation) {
        return "{\"evaluationId\":" + evaluation.getId()
                + ",\"evaluatorId\":" + evaluation.getEvaluator().getId()
                + ",\"evaluateeId\":" + evaluation.getEvaluatee().getId()
                + ",\"collaborationScore\":" + evaluation.getCollaborationScore()
                + ",\"initiativeScore\":" + evaluation.getInitiativeScore()
                + ",\"communicationScore\":" + evaluation.getCommunicationScore()
                + ",\"outputScore\":" + evaluation.getOutputScore() + "}";
    }

    private boolean hasUniformScores(PeerEvaluationCreateRequest request) {
        int firstScore = request.collaborationScore();
        return firstScore == request.initiativeScore() &&
                firstScore == request.communicationScore() &&
                firstScore == request.outputScore();
    }

    private void requireEvaluationOpen(Project project) {
        if (!project.isEvaluatingState(TimeUtil.today())) {
            throw new ApiException(EvaluationErrorCode.NOT_EVALUATING_STATE);
        }
    }
}
