package com.plog.domain.project.service;

import com.plog.domain.project.dto.response.ProjectInviteReissueResponse;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ProjectInviteService {

    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final InviteTokenService inviteTokenService;
    private final String inviteBaseUrl;

    public ProjectInviteService(
            ProjectRepository projectRepository,
            ProjectAccessService projectAccessService,
            InviteTokenService inviteTokenService,
            @Value("${plog.invite.base-url}") String inviteBaseUrl
    ) {
        this.projectRepository = projectRepository;
        this.projectAccessService = projectAccessService;
        this.inviteTokenService = inviteTokenService;
        this.inviteBaseUrl = inviteBaseUrl;
    }

    public ProjectInviteReissueResponse reissue(Long projectId, Long userId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
        projectAccessService.requireOwner(projectId, userId);

        String inviteCode = inviteTokenService.rotate(projectId).rawValue();
        String inviteUrl = UriComponentsBuilder.fromUriString(inviteBaseUrl)
                .pathSegment(inviteCode)
                .toUriString();
        return new ProjectInviteReissueResponse(inviteCode, inviteUrl, true);
    }
}
