package com.plog.domain.chat.dto.response;

import com.plog.domain.user.entity.ProfilePreset;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "채팅방 참여자 프로필 미리보기")
public record ChatChannelParticipantResponse(
        @Schema(description = "사용자 ID", example = "10")
        Long userId,
        @Schema(description = "사용자 닉네임", example = "바나")
        String nickname,
        @Schema(description = "프로필 프리셋", example = "PROFILE_1")
        ProfilePreset profilePreset
) {
}
