package com.plog.domain.user.controller;

import com.plog.domain.user.controller.docs.SocialAuthControllerDoc;
import com.plog.domain.user.dto.request.SocialLoginRequest;
import com.plog.domain.user.dto.request.SocialSignupRequest;
import com.plog.domain.user.dto.response.SocialLoginResponse;
import com.plog.domain.user.dto.response.TokenResponse;
import com.plog.domain.user.entity.ProviderType;
import com.plog.domain.user.entity.SocialLoginStatus;
import com.plog.domain.user.service.SocialLoginService;
import com.plog.domain.user.service.SocialSignupService;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.AuthSuccessCode;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/oauth")
public class SocialAuthController implements SocialAuthControllerDoc {

    private final SocialLoginService socialLoginService;
    private final SocialSignupService socialSignupService;

    public SocialAuthController(SocialLoginService socialLoginService,
                                SocialSignupService socialSignupService) {
        this.socialLoginService = socialLoginService;
        this.socialSignupService = socialSignupService;
    }

    // 리터럴 경로(/signup)가 템플릿(/{provider})보다 먼저 매칭되므로 두 매핑은 충돌하지 않는다.
    @Override
    @PostMapping("/{provider}")
    public ResponseEntity<ApiResponse<SocialLoginResponse>> login(
            @PathVariable String provider,
            @Valid @RequestBody SocialLoginRequest request) {
        SocialLoginResponse response = socialLoginService.login(parseProvider(provider), request.code());
        AuthSuccessCode code = response.status() == SocialLoginStatus.LOGIN
                ? AuthSuccessCode.SOCIAL_LOGIN_SUCCESS
                : AuthSuccessCode.SOCIAL_SIGNUP_REQUIRED;
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.success(code, response));
    }

    @Override
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<TokenResponse>> signup(
            @Valid @RequestBody SocialSignupRequest request) {
        TokenResponse tokens = socialSignupService.signup(request);
        return ResponseEntity.status(AuthSuccessCode.SOCIAL_SIGNUP_COMPLETED.getHttpStatus())
                .body(ApiResponse.success(AuthSuccessCode.SOCIAL_SIGNUP_COMPLETED, tokens));
    }

    private ProviderType parseProvider(String provider) {
        return switch (provider == null ? "" : provider.trim().toLowerCase()) {
            case "kakao" -> ProviderType.KAKAO;
            case "google" -> ProviderType.GOOGLE;
            default -> throw new ApiException(AuthErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);
        };
    }
}
