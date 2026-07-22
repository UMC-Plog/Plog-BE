package com.plog.domain.project.service;

import com.plog.domain.project.dto.response.ProjectInvitationPreviewResponse;
import com.plog.domain.project.entity.Project;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectInvitationPreviewService {

    private final InviteTokenService inviteTokenService;

    @Transactional(readOnly = true)
    public ProjectInvitationPreviewResponse preview(Long userId, String inviteCode) {
        if (userId == null) {
            throw new ApiException(AuthErrorCode.INVALID_TOKEN);
        }

        Project project = inviteTokenService.findProjectByRawToken(inviteCode)
                .orElseThrow(() -> new ApiException(ProjectErrorCode.INVALID_INVITE_CODE));

        return new ProjectInvitationPreviewResponse(
                project.getId(),
                project.getProjectName(),
                project.getProjectType(),
                project.getEndDay()
        );
    }
}
