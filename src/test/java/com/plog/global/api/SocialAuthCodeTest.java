package com.plog.global.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.response.AuthSuccessCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SocialAuthCodeTest {

    @Test
    @DisplayName("소셜 성공 코드는 AUTH011~013으로 정의된다")
    void successCodes() {
        assertThat(AuthSuccessCode.SOCIAL_LOGIN_SUCCESS.getCode()).isEqualTo("AUTH011");
        assertThat(AuthSuccessCode.SOCIAL_SIGNUP_REQUIRED.getCode()).isEqualTo("AUTH012");
        assertThat(AuthSuccessCode.SOCIAL_SIGNUP_COMPLETED.getCode()).isEqualTo("AUTH013");
        assertThat(AuthSuccessCode.SOCIAL_SIGNUP_COMPLETED.getHttpStatus()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("소셜 에러 코드는 AUTH019~024로 정의된다")
    void errorCodes() {
        assertThat(AuthErrorCode.UNSUPPORTED_SOCIAL_PROVIDER.getCode()).isEqualTo("AUTH019");
        assertThat(AuthErrorCode.SOCIAL_AUTHORIZATION_FAILED.getCode()).isEqualTo("AUTH020");
        assertThat(AuthErrorCode.SOCIAL_EMAIL_NOT_PROVIDED.getCode()).isEqualTo("AUTH021");
        assertThat(AuthErrorCode.SOCIAL_TICKET_INVALID.getCode()).isEqualTo("AUTH022");
        assertThat(AuthErrorCode.SOCIAL_TICKET_EXPIRED.getCode()).isEqualTo("AUTH023");
        assertThat(AuthErrorCode.SOCIAL_PROVIDER_CONFIGURATION_ERROR.getCode()).isEqualTo("AUTH024");
        assertThat(AuthErrorCode.SOCIAL_ACCOUNT_ALREADY_REGISTERED.getCode()).isEqualTo("AUTH025");
        assertThat(AuthErrorCode.SOCIAL_AUTHORIZATION_FAILED.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(AuthErrorCode.SOCIAL_ACCOUNT_ALREADY_REGISTERED.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    }
}
