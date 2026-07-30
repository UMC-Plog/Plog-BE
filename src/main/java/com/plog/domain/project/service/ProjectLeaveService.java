package com.plog.domain.project.service;

import com.plog.domain.project.dto.response.ProjectLeaveResponse;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectLeaveService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectPurgeService projectPurgeService;

    @Transactional
    public ProjectLeaveResponse leave(Long projectId, Long userId) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND));
        ProjectMember projectMember = projectMemberRepository
                .findByProjectIdAndUserIdAndStatusForUpdate(projectId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ProjectErrorCode.PROJECT_MEMBER_REQUIRED));

        long activeMemberCount =
                projectMemberRepository.countByProjectIdAndStatus(projectId, MemberStatus.ACTIVE);

        if (projectMember.getRole() == ProjectRole.OWNER && activeMemberCount > 1) {
            throw new ApiException(ProjectErrorCode.OWNER_MUST_TRANSFER);
        }

        if (activeMemberCount == 1) {
            projectPurgeService.purge(projectId);
            projectRepository.delete(project);
        } else {
            projectMember.leave();
            projectMemberRepository.saveAndFlush(projectMember);
        }

        return new ProjectLeaveResponse(true);
    }
}