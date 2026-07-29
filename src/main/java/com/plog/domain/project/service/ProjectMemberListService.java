package com.plog.domain.project.service;

import com.plog.domain.project.dto.response.ActiveProjectMemberResponse;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.repository.ProjectMemberRepository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectMemberListService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAccessService projectAccessService;

    public List<ActiveProjectMemberResponse> getActiveMembers(
            Long projectId,
            Long userId
    ) {
        projectAccessService.requireActiveMember(projectId, userId);

        return projectMemberRepository
                .findAllByProjectIdAndStatusOrderByIdAsc(
                        projectId,
                        MemberStatus.ACTIVE
                )
                .stream()
                .map(ActiveProjectMemberResponse::from)
                .toList();
    }
}
