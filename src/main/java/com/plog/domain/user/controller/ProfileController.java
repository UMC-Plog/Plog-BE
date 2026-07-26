package com.plog.domain.user.controller;

import com.plog.domain.user.controller.docs.ProfileControllerDoc;
import com.plog.domain.user.dto.request.ProfileUpdateRequest;
import com.plog.domain.user.dto.response.ProfileResponse;
import com.plog.domain.user.service.ProfileService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.ProfileSuccessCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/profile")
public class ProfileController implements ProfileControllerDoc {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(
            @AuthenticationPrincipal Long userId
    ) {
        ProfileResponse response = profileService.getProfile(userId);
        return ResponseEntity.status(ProfileSuccessCode.PROFILE_RETRIEVED.getHttpStatus())
                .body(ApiResponse.success(ProfileSuccessCode.PROFILE_RETRIEVED, response));
    }

    @Override
    @GetMapping("/nickname/check")
    public ResponseEntity<ApiResponse<Void>> checkNickname(
            @AuthenticationPrincipal Long userId,
            @RequestParam String nickname
    ) {
        profileService.checkNicknameAvailable(userId, nickname);
        return ResponseEntity.status(ProfileSuccessCode.NICKNAME_AVAILABLE.getHttpStatus())
                .body(ApiResponse.success(ProfileSuccessCode.NICKNAME_AVAILABLE, null));
    }

    @Override
    @PatchMapping
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @AuthenticationPrincipal Long userId,
            @RequestBody ProfileUpdateRequest request
    ) {
        profileService.updateProfile(userId, request);
        return ResponseEntity.status(ProfileSuccessCode.PROFILE_UPDATED.getHttpStatus())
                .body(ApiResponse.success(ProfileSuccessCode.PROFILE_UPDATED, null));
    }
}
