package com.plog.domain.evaluation.service;

import com.plog.domain.evaluation.dto.response.EvaluationTargetListResponse;
import com.plog.domain.evaluation.dto.response.EvaluationTargetResponse;
import com.plog.domain.evaluation.repository.PeerReviewRepository;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectMemberStatus;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.api.code.ErrorCode;
import com.plog.global.api.exception.ApiException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PeerReviewRepository peerReviewRepository;

    public EvaluationTargetListResponse getEvaluationTargets(String projectId, String userId) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        if (!project.isEvaluationReady()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }

        ProjectMember currentMember = projectMemberRepository.findByProjectIdAndUserIdAndProjectStatus(
                        projectId,
                        userId,
                        ProjectMemberStatus.ACTIVE
                )
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN));

        List<ProjectMember> otherMembers = projectMemberRepository
                .findAllByProjectIdAndProjectStatusAndProjectMemberIdNot(
                        projectId,
                        ProjectMemberStatus.ACTIVE,
                        currentMember.getProjectMemberId()
                );

        List<EvaluationTargetResponse> targets = otherMembers.stream()
                .map(targetMember -> {
                    boolean isEvaluated = peerReviewRepository.existsByProjectIdAndEvaluatorIdAndEvaluateeId(
                            projectId,
                            currentMember.getProjectMemberId(),
                            targetMember.getProjectMemberId()
                    );

                    return new EvaluationTargetResponse(
                            targetMember.getProjectMemberId(),
                            targetMember.getAnNickname(),
                            isEvaluated
                    );
                })
                .toList();

        return new EvaluationTargetListResponse(targets);
    }
}
