package com.plog.domain.project.service;

import com.plog.domain.project.dto.response.ProjectLeaveResponse;
import com.plog.domain.project.entity.MemberStatus;
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
        // 프로젝트 존재 확인 + 비관적 락 획득(동시 나가기 직렬화). 엔티티 자체는 이후 벌크 삭제로 처리한다.
        projectRepository.findByIdForUpdate(projectId)
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
            // purge가 하위 데이터와 프로젝트 행까지 벌크로 삭제한다.
            // findByIdForUpdate로 잡은 비관적 락은 그대로 유효하다(락만 걸고 벌크 삭제).
            projectPurgeService.purge(projectId);
        } else {
            projectMember.leave();
            projectMemberRepository.saveAndFlush(projectMember);
        }

        return new ProjectLeaveResponse(true);
    }
}