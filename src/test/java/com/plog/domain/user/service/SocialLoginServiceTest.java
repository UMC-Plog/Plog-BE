package com.plog.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.plog.domain.user.dto.response.SocialLoginResponse;
import com.plog.domain.user.dto.response.TokenResponse;
import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.user.entity.ProviderType;
import com.plog.domain.user.entity.SocialLoginStatus;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SocialLoginServiceTest {

    private static final SocialIdentity IDENTITY =
            new SocialIdentity(ProviderType.KAKAO, "kakao-1", "a@plog.test");

    private SocialOAuthClient kakaoClient;
    private UserRepository userRepository;
    private SocialSignupTicketService ticketService;
    private EmailAvailabilityService emailAvailabilityService;
    private TokenIssuer tokenIssuer;
    private SocialLoginService service;

    @BeforeEach
    void setUp() {
        kakaoClient = mock(SocialOAuthClient.class);
        given(kakaoClient.provider()).willReturn(ProviderType.KAKAO);
        lenient().when(kakaoClient.exchange("code")).thenReturn(IDENTITY);

        userRepository = mock(UserRepository.class);
        ticketService = mock(SocialSignupTicketService.class);
        emailAvailabilityService = mock(EmailAvailabilityService.class);
        tokenIssuer = mock(TokenIssuer.class);
        service = new SocialLoginService(List.of(kakaoClient), userRepository,
                ticketService, emailAvailabilityService, tokenIssuer);
    }

    private User socialUser() {
        return User.createSocial("a@plog.test", "홍길동", "바나나",
                ProviderType.KAKAO, "kakao-1", ProfilePreset.OTTER);
    }

    @Test
    @DisplayName("이미 가입된 소셜 계정은 바로 토큰을 발급한다")
    void existingUserGetsTokens() {
        given(userRepository.findByProviderTypeAndProviderId(ProviderType.KAKAO, "kakao-1"))
                .willReturn(Optional.of(socialUser()));
        given(tokenIssuer.issue(any())).willReturn(new TokenResponse("access", "refresh"));

        SocialLoginResponse response = service.login(ProviderType.KAKAO, "code");

        assertThat(response.status()).isEqualTo(SocialLoginStatus.LOGIN);
        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.ticket()).isNull();
        verify(ticketService, never()).issue(any());
    }

    @Test
    @DisplayName("탈퇴 유예 중인 소셜 계정은 WITHDRAWN_ACCOUNT")
    void withdrawnUserRejected() {
        User withdrawn = socialUser();
        withdrawn.withdraw(LocalDateTime.of(2026, 7, 25, 12, 0));
        given(userRepository.findByProviderTypeAndProviderId(ProviderType.KAKAO, "kakao-1"))
                .willReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> service.login(ProviderType.KAKAO, "code"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.WITHDRAWN_ACCOUNT);
        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    @DisplayName("신규 계정은 가입 티켓과 이메일을 돌려준다")
    void newUserGetsTicket() {
        given(userRepository.findByProviderTypeAndProviderId(ProviderType.KAKAO, "kakao-1"))
                .willReturn(Optional.empty());
        given(ticketService.issue(IDENTITY)).willReturn("raw-ticket");

        SocialLoginResponse response = service.login(ProviderType.KAKAO, "code");

        assertThat(response.status()).isEqualTo(SocialLoginStatus.SIGNUP_REQUIRED);
        assertThat(response.ticket()).isEqualTo("raw-ticket");
        assertThat(response.email()).isEqualTo("a@plog.test");
        assertThat(response.accessToken()).isNull();
    }

    @Test
    @DisplayName("같은 이메일의 다른 계정이 있으면 티켓을 발급하지 않는다")
    void emailConflictBlocksTicket() {
        given(userRepository.findByProviderTypeAndProviderId(ProviderType.KAKAO, "kakao-1"))
                .willReturn(Optional.empty());
        doThrow(new ApiException(AuthErrorCode.EMAIL_DUPLICATED_LOCAL))
                .when(emailAvailabilityService).assertAvailableForSignup("a@plog.test");

        assertThatThrownBy(() -> service.login(ProviderType.KAKAO, "code"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.EMAIL_DUPLICATED_LOCAL);
        verify(ticketService, never()).issue(any());
    }

    @Test
    @DisplayName("구현체가 없는 provider는 UNSUPPORTED_SOCIAL_PROVIDER")
    void unsupportedProvider() {
        assertThatThrownBy(() -> service.login(ProviderType.GOOGLE, "code"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);
    }
}
