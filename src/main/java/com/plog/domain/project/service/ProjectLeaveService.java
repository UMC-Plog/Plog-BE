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

        // 생성자(OWNER)가 다른 팀원을 남겨두고 나가는 경우, 프로젝트에 OWNER가 항상 1명 존재해야 하는
        // 도메인 불변식을 유지하기 위해 가장 먼저 합류한 활성 멤버에게 소유권을 자동 위임한 뒤 나간다.
        if (projectMember.getRole() == ProjectRole.OWNER && activeMemberCount > 1) {
            ProjectMember successor = projectMemberRepository
                    .findAllByProjectIdAndStatusOrderByIdAsc(projectId, MemberStatus.ACTIVE)
                    .stream()
                    .filter(candidate -> !candidate.getId().equals(projectMember.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(ProjectErrorCode.OWNER_MUST_TRANSFER));
            projectMember.transferOwnershipTo(successor);
            projectMemberRepository.saveAndFlush(successor);
        }

        if (activeMemberCount == 1) {
            // 마지막 활성 멤버가 나가면 역할(OWNER/MEMBER)과 무관하게 프로젝트를 영구 삭제한다.
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