package com.plog.domain.chat.dto.response;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.user.entity.ProfilePreset;

public record MentionableMemberResponse(
        Long projectMemberId,
        Long userId,
        String nickname,
        ProfilePreset profilePreset
) {
    public static MentionableMemberResponse from(ProjectMember member) {
        String nickname = member.getAnNickname() != null && !member.getAnNickname().isBlank()
                ? member.getAnNickname()
                : member.getUser().getNickname();
        return new MentionableMemberResponse(
                member.getId(),
                member.getUser().getId(),
                nickname,
                member.getUser().getProfilePreset()
        );
    }
}