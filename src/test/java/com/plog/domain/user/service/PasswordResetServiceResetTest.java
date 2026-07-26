package com.plog.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.plog.domain.user.entity.EmailVerification;
import com.plog.domain.user.entity.EmailVerificationPurpose;
import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.RefreshTokenRepository;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.config.EmailVerificationProperties;
import com.plog.global.util.HashUtil;
import com.plog.global.util.TimeUtil;
import com.plog.infrastructure.mail.MailSender;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordResetServiceResetTest {

    private EmailVerificationCodeService codeService;
    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordEncoder passwordEncoder;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        codeService = mock(EmailVerificationCodeService.class);
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new PasswordResetService(codeService, userRepository,
                refreshTokenRepository, mock(MailSender.class), passwordEncoder,
                new EmailVerificationProperties(6, Duration.ofMinutes(5), 5, Duration.ofSeconds(60)));
    }

    private EmailVerification verifiedRow() {
        EmailVerification row = EmailVerification.issue("a@plog.test",
                EmailVerificationPurpose.PASSWORD_RESET,
                HashUtil.sha256Hex("123456"),
                TimeUtil.nowUtc().plusMinutes(5), TimeUtil.nowUtc());
        row.markVerified();
        return row;
    }

    @Test
    @DisplayName("코드 검증은 공통 컴포넌트에 PASSWORD_RESET 목적으로 위임한다")
    void verifyDelegatesWithResetPurpose() {
        service.verify("a@plog.test", "123456");

        verify(codeService).verifyCode("a@plog.test",
                EmailVerificationPurpose.PASSWORD_RESET, "123456");
    }

    @Test
    @DisplayName("만료된 인증으로는 재설정할 수 없다")
    void expiredVerificationRejected() {
        EmailVerification expired = EmailVerification.issue("a@plog.test",
                EmailVerificationPurpose.PASSWORD_RESET, "hash",
                TimeUtil.nowUtc().minusMinutes(1), TimeUtil.nowUtc().minusMinutes(6));
        expired.markVerified();
        given(codeService.requireVerified("a@plog.test", EmailVerificationPurpose.PASSWORD_RESET))
                .willReturn(expired);

        assertThatThrownBy(() -> service.reset("a@plog.test", "plog1234", "plog1234"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.EMAIL_NOT_VERIFIED);
    }

    @Test
    @DisplayName("재설정 성공 시 비밀번호를 바꾸고 인증을 소비하고 리프레시 토큰을 폐기한다")
    void resetChangesPasswordAndClearsSessions() {
        EmailVerification row = verifiedRow();
        User user = User.createLocal("a@plog.test", "old-encoded", "홍길동", "바나나", ProfilePreset.OTTER);
        given(codeService.requireVerified("a@plog.test", EmailVerificationPurpose.PASSWORD_RESET))
                .willReturn(row);
        given(userRepository.findByEmail("a@plog.test")).willReturn(Optional.of(user));
        given(passwordEncoder.encode(anyString())).willReturn("new-encoded");

        service.reset("a@plog.test", "plog1234", "plog1234");

        assertThat(user.getPassword()).isEqualTo("new-encoded");
        verify(codeService).consume(row);
        verify(refreshTokenRepository).deleteAllByUserId(user.getId());
    }
}
