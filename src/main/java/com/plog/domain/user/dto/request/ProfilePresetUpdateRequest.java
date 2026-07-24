package com.plog.domain.user.dto.request;

import com.plog.domain.user.entity.ProfilePreset;
import jakarta.validation.constraints.NotNull;

/**
 * 프로필 프리셋 변경 요청. 변경 화면엔 8종만 있고 "기본으로 되돌림" 옵션이 없어 preset은 필수.
 * (가입 시 미선택=null은 SignupRequest 경로에서만 허용.)
 */
public record ProfilePresetUpdateRequest(
        @NotNull ProfilePreset preset
) {
}
