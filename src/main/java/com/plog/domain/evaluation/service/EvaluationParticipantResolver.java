package com.plog.domain.evaluation.service;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.global.api.code.ErrorCode;
import com.plog.global.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class EvaluationParticipantResolver {

    private final ProjectMemberRepository projectMemberRepository;

    ProjectMember requireEvaluator(Long projectId, Long userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN));
    }

    ProjectMember requireEvaluatee(Long projectId, Long targetMemberId) {
        ProjectMember evaluatee = projectMemberRepository.findById(targetMemberId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!evaluatee.getProject().getId().equals(projectId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return evaluatee;
    }
}
