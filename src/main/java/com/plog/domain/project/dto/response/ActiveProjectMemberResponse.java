package com.plog.domain.project.dto.response;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.user.entity.ProfilePreset;

public record ActiveProjectMemberResponse(
        Long projectMemberId,
        String nickname,
        ProfilePreset profilePreset
) {

    public static ActiveProjectMemberResponse from(ProjectMember projectMember) {
        return new ActiveProjectMemberResponse(
                projectMember.getId(),
                resolveNickname(projectMember),
                projectMember.getUser().getProfilePreset()
        );
    }

    private static String resolveNickname(ProjectMember projectMember) {
        String projectNickname = projectMember.getAnNickname();

        if (projectNickname == null || projectNickname.isBlank()) {
            return projectMember.getUser().getNickname();
        }

        return projectNickname.trim();
    }
}