package com.plog.domain.user.service;

import com.plog.domain.user.entity.EmailVerificationPurpose;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.config.EmailVerificationProperties;
import com.plog.infrastructure.mail.MailSender;
import org.springframework.stereotype.Service;

/**
 * 이메일 인증 코드 발송/검증.
 * <p>
 * 의도적으로 클래스/메서드 레벨 @Transactional 을 걸지 않는다.
 * - 발송: 메일(외부 I/O)을 트랜잭션 안에 넣지 않기 위해.
 * - 검증: 실패 시 시도횟수 증가를 "커밋한 뒤" 예외를 던져야 한다. @Transactional 안에서 던지면
 *   증가분까지 롤백돼 무차별 대입 제한이 무력화된다. → save() 단위로 커밋하고 이후에 throw.
 */
@Service
public class EmailVerificationService {

    private final EmailVerificationCodeService emailVerificationCodeService;
    private final UserRepository userRepository;
    private final MailSender mailSender;
    private final EmailVerificationProperties properties;

    public EmailVerificationService(EmailVerificationCodeService emailVerificationCodeService,
                                    UserRepository userRepository, MailSender mailSender,
                                    EmailVerificationProperties properties) {
        this.emailVerificationCodeService = emailVerificationCodeService;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.properties = properties;
    }

    /** 인증 코드 발송. 중복 이메일은 발송 전에 차단하고, 재전송 쿨다운을 강제한다. */
    public void sendCode(String email) {
        assertEmailNotRegistered(email); // 유가입자에게 메일 보내는 낭비 차단

        String rawCode = emailVerificationCodeService.issueCode(email, EmailVerificationPurpose.SIGNUP);

        // 외부 I/O — 트랜잭션 밖
        mailSender.send(email, "[Plog] 이메일 인증 코드",
                "인증 코드: " + rawCode + "\n"
                        + properties.ttl().toMinutes() + "분 내에 입력해 주세요.");
    }

    /** 인증 코드 검증. 성공 시 verified 상태로 마킹(가입 시 이 상태를 확인). */
    public void verify(String email, String code) {
        emailVerificationCodeService.verifyCode(email, EmailVerificationPurpose.SIGNUP, code);
    }

    private void assertEmailNotRegistered(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return;
        }
        if (user.isWithdrawn()) {
            throw new ApiException(AuthErrorCode.EMAIL_WITHDRAWAL_PENDING);
        }
        // 가입 수단까지 구분해 반환 — 소셜 가입자가 일반 로그인 화면에서 갇히지 않도록
        throw user.isSocialUser()
                ? new ApiException(AuthErrorCode.EMAIL_DUPLICATED_SOCIAL)
                : new ApiException(AuthErrorCode.EMAIL_DUPLICATED_LOCAL);
    }
}
