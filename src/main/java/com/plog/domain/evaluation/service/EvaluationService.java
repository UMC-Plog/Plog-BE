package com.plog.domain.evaluation.service;

import com.plog.domain.evaluation.dto.request.PeerEvaluationCreateRequest;
import com.plog.domain.evaluation.dto.response.EvaluationTargetResponse;
import com.plog.domain.evaluation.dto.response.PeerEvaluationCreateResponse;
import com.plog.domain.evaluation.dto.response.PeerEvaluationDetailResponse;
import com.plog.domain.evaluation.dto.response.TargetMemberDto;
import com.plog.domain.evaluation.entity.PeerEvaluation;
import com.plog.domain.evaluation.repository.PeerEvaluationRepository;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.api.code.ErrorCode;
import com.plog.global.api.error.EvaluationErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PeerEvaluationRepository peerEvaluationRepository;

    public EvaluationTargetResponse getEvaluationTargets(Long projectId, Long userId) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        ProjectMember currentMember = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN));

        List<ProjectMember> allMembers = projectMemberRepository.findAllWithUserByProjectId(projectId);

        Set<Long> evaluatedTargetIds = peerEvaluationRepository.findEvaluatedTargetIds(currentMember);

        List<TargetMemberDto> targets = allMembers.stream()
                .filter(member -> !member.getId().equals(currentMember.getId())) // 본인 제외
                .map(member -> {
                    boolean isEvaluated = evaluatedTargetIds.contains(member.getId());

                    return TargetMemberDto.builder()
                            .projectMemberId(member.getId())
                            .nickname(member.getAnNickname() != null ? member.getAnNickname() : member.getUser().getNickname())
                            .isEvaluated(isEvaluated)
                            .build();
                })
                .collect(Collectors.toList());

        return new EvaluationTargetResponse(targets);
    }

    public PeerEvaluationDetailResponse getPeerEvaluationDetail(Long projectId, Long targetMemberId, Long userId) {

        ProjectMember evaluator = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN));

        ProjectMember evaluatee = projectMemberRepository.findById(targetMemberId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        if (!evaluatee.getProject().getId().equals(projectId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }

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

        ProjectMember evaluator = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN));

        ProjectMember evaluatee = projectMemberRepository.findById(targetMemberId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        if (!evaluatee.getProject().getId().equals(projectId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }

        if (!evaluatee.getProject().isEvaluatingState(TimeUtil.todayUtc())) {
            throw new ApiException(EvaluationErrorCode.NOT_EVALUATING_STATE);
        }

        if (evaluator.getId().equals(evaluatee.getId())) {
            throw new ApiException(EvaluationErrorCode.CANNOT_EVALUATE_SELF);
        }

        if (peerEvaluationRepository.findByEvaluatorIdAndEvaluateeId(evaluator.getId(), evaluatee.getId()).isPresent()) {
            throw new ApiException(EvaluationErrorCode.ALREADY_EVALUATED);
        }

        boolean isNudgeTriggered = checkNudgeCondition(request);

        PeerEvaluation evaluation = PeerEvaluation.builder()
                .evaluator(evaluator)
                .evaluatee(evaluatee)
                .collaborationScore(request.collaborationScore())
                .initiativeScore(request.initiativeScore())
                .responsibilityScore(request.responsibilityScore())
                .communicationScore(request.communicationScore())
                .outputScore(request.outputScore())
                .keywords(request.keywords())
                .feedback(request.feedback())
                .build();

        peerEvaluationRepository.save(evaluation);

        return new PeerEvaluationCreateResponse(evaluation.getId(), isNudgeTriggered);
    }

    @Transactional
    public PeerEvaluationCreateResponse updatePeerEvaluation(
            Long projectId,
            Long targetMemberId,
            Long userId,
            PeerEvaluationCreateRequest request
    ) {
        ProjectMember evaluator = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN));

        ProjectMember evaluatee = projectMemberRepository.findById(targetMemberId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        if (!evaluatee.getProject().getId().equals(projectId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }

        if (evaluatee.getProject().isCompleted()) {
            throw new ApiException(EvaluationErrorCode.CANNOT_MODIFY_EVALUATION_AFTER_PUBLISH);
        }

        PeerEvaluation evaluation = peerEvaluationRepository
                .findByEvaluatorIdAndEvaluateeId(evaluator.getId(), evaluatee.getId())
                .orElseThrow(() -> new ApiException(EvaluationErrorCode.EVALUATION_NOT_FOUND));

        evaluation.update(
                request.collaborationScore(),
                request.initiativeScore(),
                request.responsibilityScore(),
                request.communicationScore(),
                request.outputScore(),
                request.keywords(),
                request.feedback()
        );

        return new PeerEvaluationCreateResponse(evaluation.getId(), checkNudgeCondition(request));
    }

    private boolean checkNudgeCondition(PeerEvaluationCreateRequest request) {
        int firstScore = request.collaborationScore();
        return firstScore == request.initiativeScore() &&
                firstScore == request.responsibilityScore() &&
                firstScore == request.communicationScore() &&
                firstScore == request.outputScore();
    }
}
