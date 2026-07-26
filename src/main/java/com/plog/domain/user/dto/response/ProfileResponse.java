package com.plog.domain.user.dto.response;

import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "마이페이지 프로필 조회 응답")
public record ProfileResponse(
        @Schema(description = "가입 이메일 (변경 불가)", example = "hello@plog.com")
        String email,
        @Schema(description = "실명", example = "홍길동")
        String name,
        @Schema(description = "닉네임", example = "바나나")
        String nickname,
        @Schema(description = "프로필 아바타 프리셋. null이면 기본(회색) 아바타",
                example = "OTTER", nullable = true)
        ProfilePreset profilePreset,
        @Schema(description = "실명 변경 가능 여부. false면 이미 1회 변경해 [변경] 버튼을 비활성화해야 한다",
                example = "true")
        boolean nameChangeAvailable
) {

    public static ProfileResponse from(User user) {
        return new ProfileResponse(
                user.getEmail(),
                user.getName(),
                user.getNickname(),
                user.getProfilePreset(),
                user.isNameChangeAvailable());
    }
}
