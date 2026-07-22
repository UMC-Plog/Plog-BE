package com.plog.domain.chat.dto.response;

public record ChatChannelParticipantResponse(
        Long userId,
        String nickname,
        String profileImageUrl
) {
}
