package com.plog.domain.user.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.plog.domain.user.entity.SocialLoginStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 소셜 인증 응답. status에 따라 채워지는 필드가 다르다.
 * LOGIN이면 토큰만, SIGNUP_REQUIRED면 ticket과 email만 내려간다(나머지는 응답에서 생략).
 */
@Schema(description = "소셜 인증 응답")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SocialLoginResponse(
        @Schema(description = "LOGIN=로그인 완료, SIGNUP_REQUIRED=약관·프로필 입력 필요", example = "LOGIN")
        SocialLoginStatus status,
        @Schema(description = "액세스 토큰 (status=LOGIN)", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,
        @Schema(description = "리프레시 토큰 (status=LOGIN)", example = "eyJhbGciOiJIUzI1NiJ9...")
        String refreshToken,
        @Schema(description = "가입 티켓 (status=SIGNUP_REQUIRED). 가입 완료 API에 그대로 전달한다",
                example = "3xL1n0...")
        String ticket,
        @Schema(description = "provider가 검증한 이메일 (status=SIGNUP_REQUIRED). 약관 화면 안내용",
                example = "user@kakao.com")
        String email
) {
    public static SocialLoginResponse login(TokenResponse tokens) {
        return new SocialLoginResponse(SocialLoginStatus.LOGIN,
                tokens.accessToken(), tokens.refreshToken(), null, null);
    }

    public static SocialLoginResponse signupRequired(String ticket, String email) {
        return new SocialLoginResponse(SocialLoginStatus.SIGNUP_REQUIRED, null, null, ticket, email);
    }
}
