package com.plog.domain.project.service;

import com.plog.domain.project.dto.request.ProjectRoleDelegationRequest;
import com.plog.domain.project.dto.response.ProjectRoleDelegationResponse;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.api.exception.ApiException;
import com.plog.global.api.error.ProjectErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectRoleDelegationService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Transactional
    public ProjectRoleDelegationResponse delegateRole(
            Long projectId,
            Long userId,
            Long targetMemberId,
            ProjectRoleDelegationRequest request
    ) {
        validateRequestedRole(request);

        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND));
        ProjectMember ownerMember = projectMemberRepository
                .findByProjectIdAndUserIdAndStatusForUpdate(projectId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ProjectErrorCode.PROJECT_MEMBER_REQUIRED));
        if (ownerMember.getRole() != ProjectRole.OWNER) {
            throw new ApiException(ProjectErrorCode.PROJECT_SETTING_PERMISSION_DENIED);
        }

        ProjectMember targetMember = projectMemberRepository
                .findByProjectIdAndIdAndStatusForUpdate(projectId, targetMemberId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ProjectErrorCode.MEMBER_NOT_FOUND));

        if (ownerMember.getId().equals(targetMember.getId())
                || targetMember.getRole() == ProjectRole.OWNER) {
            throw new ApiException(ProjectErrorCode.INVALID_PROJECT_ROLE_TRANSFER);
        }

        ownerMember.transferOwnershipTo(targetMember);
        projectMemberRepository.saveAndFlush(targetMember);

        return new ProjectRoleDelegationResponse(project.getId(), targetMember.getId());
    }

    private void validateRequestedRole(ProjectRoleDelegationRequest request) {
        if (request == null || request.role() != ProjectRole.OWNER) {
            throw new ApiException(ProjectErrorCode.INVALID_PROJECT_ROLE_TRANSFER);
        }
    }
}
