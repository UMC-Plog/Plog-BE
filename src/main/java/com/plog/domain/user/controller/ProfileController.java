package com.plog.domain.user.controller;

import com.plog.domain.user.dto.request.ProfilePresetUpdateRequest;
import com.plog.domain.user.service.ProfileService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.ProfileSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Profile", description = "프로필 이미지(프리셋) API")
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Operation(
            summary = "프로필 프리셋 변경",
            description = """
                    로그인한 사용자의 프로필 아바타 프리셋을 변경합니다(마이페이지 › 프로필 수정 › 프로필 이미지 변경).
                    - 프리셋 8종 중 하나를 선택합니다. 커스텀 이미지 업로드는 없습니다.
                    - preset은 필수입니다(누락/오값 시 COMMON400).
                    """
    )
    @PatchMapping("/preset")
    public ResponseEntity<ApiResponse<Void>> changePreset(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ProfilePresetUpdateRequest request
    ) {
        profileService.changePreset(userId, request.preset());
        return ResponseEntity.status(ProfileSuccessCode.PROFILE_PRESET_UPDATED.getHttpStatus())
                .body(ApiResponse.success(ProfileSuccessCode.PROFILE_PRESET_UPDATED, null));
    }
}
