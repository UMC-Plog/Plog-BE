package com.plog.domain.chat.dto.response;

import com.plog.domain.user.entity.ProfilePreset;

public record ChatChannelParticipantResponse(
        Long userId,
        String nickname,
        ProfilePreset profilePreset
) {
}
