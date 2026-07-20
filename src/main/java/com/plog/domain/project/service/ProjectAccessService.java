package com.plog.domain.project.service;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectAccessService {
    private final ProjectMemberRepository projectMemberRepository;

    public ProjectMember requireActiveMember(Long projectId, Long userId) {
        if (userId == null) {
            throw new ApiException(ProjectErrorCode.PROJECT_MEMBER_REQUIRED);
        }
        ProjectMember member = projectMemberRepository
                .findByProjectIdAndUserIdAndStatus(projectId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ProjectErrorCode.PROJECT_MEMBER_REQUIRED));
        ProjectRole role = member.getRole();
        if (role != ProjectRole.OWNER && role != ProjectRole.MEMBER) {
            throw new ApiException(ProjectErrorCode.PROJECT_MEMBER_REQUIRED);
        }
        return member;
    }

    public ProjectMember requireOwner(Long projectId, Long userId) {
        ProjectMember member = requireActiveMember(projectId, userId);
        if (member.getRole() != ProjectRole.OWNER) {
            throw new ApiException(ProjectErrorCode.PROJECT_SETTING_PERMISSION_DENIED);
        }
        return member;
    }
}
