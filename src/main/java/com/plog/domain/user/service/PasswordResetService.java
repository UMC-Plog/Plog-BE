package com.plog.domain.user.service;

import com.plog.domain.user.entity.EmailVerification;
import com.plog.domain.user.entity.EmailVerificationPurpose;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.RefreshTokenRepository;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.config.EmailVerificationProperties;
import com.plog.global.util.TimeUtil;
import com.plog.infrastructure.mail.MailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 재설정. 가입용 이메일 인증(EmailVerificationService)과 검사 방향이 정반대다 —
 * 가입은 "미가입자만", 재설정은 "가입된 로컬 유저만" 통과시킨다.
 * <p>
 * EmailVerificationService 와 같은 이유로 클래스 레벨 @Transactional 을 걸지 않는다.
 * 발송은 메일(외부 I/O)을 트랜잭션에 넣지 않기 위해, 검증은 시도횟수 증가를 커밋한 뒤 예외를 던지기 위해.
 */
@Service
public class PasswordResetService {

    private static final EmailVerificationPurpose PURPOSE = EmailVerificationPurpose.PASSWORD_RESET;

    private final EmailVerificationCodeService emailVerificationCodeService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationProperties properties;

    public PasswordResetService(EmailVerificationCodeService emailVerificationCodeService,
                                UserRepository userRepository,
                                RefreshTokenRepository refreshTokenRepository,
                                MailSender mailSender,
                                PasswordEncoder passwordEncoder,
                                EmailVerificationProperties properties) {
        this.emailVerificationCodeService = emailVerificationCodeService;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    /** 재설정 코드 발송. 가입된 로컬 유저만 대상. 쿨다운은 가입 흐름과 동일 정책. */
    public void sendCode(String email) {
        requireResettableUser(email);

        String rawCode = emailVerificationCodeService.issueCode(email, PURPOSE);

        mailSender.send(email, "[Plog] 비밀번호 재설정 인증 코드",
                "인증 코드: " + rawCode + "\n"
                        + properties.ttl().toMinutes() + "분 내에 입력해 주세요.");
    }

    /** 재설정 코드 검증. 시도횟수 저장 후 예외를 던지는 무차별 대입 제한은 공통 컴포넌트가 담당한다. */
    public void verify(String email, String code) {
        emailVerificationCodeService.verifyCode(email, PURPOSE, code);
    }

    /**
     * 비밀번호 교체. 인증 행을 소비하고 기존 세션을 전부 끊는다.
     * 세션을 끊는 이유: 탈취된 리프레시 토큰이 비밀번호 변경 후에도 살아있으면 재설정이 무의미해진다.
     */
    @Transactional
    public void reset(String email, String newPassword, String newPasswordConfirm) {
        // 확인 칸 불일치는 DB를 건드리기 전에 끊는다. 사용자는 다시 입력해 재시도하므로
        // 이 시점에 인증 행을 소비하거나 세션을 끊어서는 안 된다.
        if (!newPassword.equals(newPasswordConfirm)) {
            throw new ApiException(AuthErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        EmailVerification verification = emailVerificationCodeService.requireVerified(email, PURPOSE);
        // requireVerified 는 가입 흐름과 동일하게 "존재 + 인증 완료"만 본다.
        // 재설정은 만료된 인증으로 비밀번호가 바뀌면 안 되므로 만료를 여기서 한 번 더 막는다.
        if (verification.isExpired(TimeUtil.nowUtc())) {
            throw new ApiException(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }

        User user = requireResettableUser(email);
        user.changePassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        refreshTokenRepository.deleteAllByUserId(user.getId());
        emailVerificationCodeService.consume(verification); // 인증 소비 — 재사용 방지
    }

    /** 재설정 가능한 계정인지 확인하고 반환. 미가입/소셜/탈퇴를 각각 다른 코드로 구분한다. */
    private User requireResettableUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(AuthErrorCode.EMAIL_NOT_REGISTERED));
        if (user.isWithdrawn()) {
            throw new ApiException(AuthErrorCode.WITHDRAWN_ACCOUNT);
        }
        if (user.isSocialUser()) {
            throw new ApiException(AuthErrorCode.SOCIAL_PASSWORD_RESET_NOT_ALLOWED);
        }
        return user;
    }
}
