package com.plog.domain.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.plog.domain.user.entity.EmailVerificationPurpose;
import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.user.entity.ProviderType;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.EmailVerificationRepository;
import com.plog.domain.user.repository.RefreshTokenRepository;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.config.EmailVerificationProperties;
import com.plog.infrastructure.mail.MailSender;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordResetServiceSendTest {

    private EmailVerificationRepository verificationRepository;
    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private MailSender mailSender;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        verificationRepository = mock(EmailVerificationRepository.class);
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        mailSender = mock(MailSender.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        EmailVerificationProperties properties = new EmailVerificationProperties(
                6, Duration.ofMinutes(5), 5, Duration.ofSeconds(60));
        EmailVerificationCodeService emailVerificationCodeService =
                new EmailVerificationCodeService(verificationRepository, properties);

        service = new PasswordResetService(emailVerificationCodeService, userRepository,
                refreshTokenRepository, mailSender, passwordEncoder, properties);
    }

    @Test
    @DisplayName("가입되지 않은 이메일에는 발송하지 않는다")
    void rejectsUnregisteredEmail() {
        given(userRepository.findByEmail("none@plog.test")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendCode("none@plog.test"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.EMAIL_NOT_REGISTERED);
        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("소셜 계정은 비밀번호를 재설정할 수 없다")
    void rejectsSocialUser() {
        User social = User.createSocial("s@plog.test", "홍길동", "바나나",
                ProviderType.GOOGLE, "google-1");
        given(userRepository.findByEmail("s@plog.test")).willReturn(Optional.of(social));

        assertThatThrownBy(() -> service.sendCode("s@plog.test"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SOCIAL_PASSWORD_RESET_NOT_ALLOWED);
    }

    @Test
    @DisplayName("탈퇴 처리 중인 계정은 비밀번호를 재설정할 수 없다")
    void rejectsWithdrawnUser() {
        User user = User.createLocal("a@plog.test", "encoded", "홍길동", "바나나", ProfilePreset.OTTER);
        user.withdraw(LocalDateTime.of(2026, 7, 25, 12, 0));
        given(userRepository.findByEmail("a@plog.test")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.sendCode("a@plog.test"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.WITHDRAWN_ACCOUNT);
    }

    @Test
    @DisplayName("가입된 로컬 유저에게는 PASSWORD_RESET 목적으로 코드를 발송한다")
    void sendsCodeToLocalUser() {
        User user = User.createLocal("a@plog.test", "encoded", "홍길동", "바나나", ProfilePreset.OTTER);
        given(userRepository.findByEmail("a@plog.test")).willReturn(Optional.of(user));
        given(verificationRepository.findByEmailAndPurpose(
                "a@plog.test", EmailVerificationPurpose.PASSWORD_RESET)).willReturn(Optional.empty());

        service.sendCode("a@plog.test");

        verify(verificationRepository).save(org.mockito.ArgumentMatchers.argThat(
                v -> v.getPurpose() == EmailVerificationPurpose.PASSWORD_RESET));
        verify(mailSender).send(org.mockito.ArgumentMatchers.eq("a@plog.test"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
}
