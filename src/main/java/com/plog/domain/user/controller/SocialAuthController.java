package com.plog.domain.user.controller;

import com.plog.domain.user.controller.docs.SocialAuthControllerDoc;
import com.plog.domain.user.dto.request.SocialLoginRequest;
import com.plog.domain.user.dto.request.SocialSignupRequest;
import com.plog.domain.user.dto.response.SocialLoginResponse;
import com.plog.domain.user.dto.response.TokenResponse;
import com.plog.domain.user.entity.ProviderType;
import com.plog.domain.user.entity.SocialLoginStatus;
import com.plog.domain.user.service.SocialLoginService;
import com.plog.global.security.jwt.MediaCookieFactory;
import org.springframework.http.HttpHeaders;
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
    private final MediaCookieFactory mediaCookieFactory;

    public SocialAuthController(SocialLoginService socialLoginService,
                                SocialSignupService socialSignupService,
                                MediaCookieFactory mediaCookieFactory) {
        this.socialLoginService = socialLoginService;
        this.socialSignupService = socialSignupService;
        this.mediaCookieFactory = mediaCookieFactory;
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

        // SIGNUP_REQUIRED 에는 토큰이 없다(ticket/email 만 내려간다) → 쿠키를 심지 않는다.
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(code.getHttpStatus());
        if (response.status() == SocialLoginStatus.LOGIN) {
            builder.header(HttpHeaders.SET_COOKIE,
                    mediaCookieFactory.issue(response.accessToken()).toString());
        }
        return builder.body(ApiResponse.success(code, response));
    }

    @Override
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<TokenResponse>> signup(
            @Valid @RequestBody SocialSignupRequest request) {
        TokenResponse tokens = socialSignupService.signup(request);
        return ResponseEntity.status(AuthSuccessCode.SOCIAL_SIGNUP_COMPLETED.getHttpStatus())
                .header(HttpHeaders.SET_COOKIE,
                        mediaCookieFactory.issue(tokens.accessToken()).toString())
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
