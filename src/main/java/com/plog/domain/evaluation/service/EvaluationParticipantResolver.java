package com.plog.domain.evaluation.service;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.global.api.code.ErrorCode;
import com.plog.global.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class EvaluationParticipantResolver {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAccessService projectAccessService;

    ProjectMember requireEvaluator(Long projectId, Long userId) {
        return projectAccessService.requireActiveMember(projectId, userId);
    }

    ProjectMember requireEvaluatee(Long projectId, Long targetMemberId) {
        ProjectMember evaluatee = projectMemberRepository.findById(targetMemberId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!evaluatee.getProject().getId().equals(projectId)
                || evaluatee.getStatus() != MemberStatus.ACTIVE) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return evaluatee;
    }
}
