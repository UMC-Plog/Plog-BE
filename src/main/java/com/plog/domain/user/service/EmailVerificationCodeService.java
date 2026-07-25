package com.plog.domain.user.service;

import com.plog.domain.user.entity.EmailVerification;
import com.plog.domain.user.entity.EmailVerificationPurpose;
import com.plog.domain.user.repository.EmailVerificationRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.config.EmailVerificationProperties;
import com.plog.global.util.HashUtil;
import com.plog.global.util.TimeUtil;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/**
 * 이메일 인증 코드의 발급/검증 공통 메커니즘(쿨다운, 생성, 해시, TTL, 시도횟수 제한).
 * SIGNUP/PASSWORD_RESET 등 목적(purpose)에 무관하게 동작하고, 가입 가능 여부 같은 목적별 정책과
 * 메일 발송은 호출자가 담당한다.
 * <p>
 * 의도적으로 클래스/메서드 레벨 @Transactional 을 걸지 않는다.
 * - 발급: 메일(외부 I/O)을 트랜잭션 안에 넣지 않기 위해. 메일 발송은 호출자 책임.
 * - 검증: 실패 시 시도횟수 증가를 "커밋한 뒤" 예외를 던져야 한다. @Transactional 안에서 던지면
 *   증가분까지 롤백돼 무차별 대입 제한이 무력화된다. → save() 단위로 커밋하고 이후에 throw.
 */
@Service
public class EmailVerificationCodeService {

    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailVerificationProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailVerificationCodeService(EmailVerificationRepository emailVerificationRepository,
                                        EmailVerificationProperties properties) {
        this.emailVerificationRepository = emailVerificationRepository;
        this.properties = properties;
    }

    /** 인증 코드 발급. 재전송 쿨다운을 강제하고, 코드를 생성/해시해 저장한 뒤 원문 코드를 반환한다. */
    public String issueCode(String email, EmailVerificationPurpose purpose) {
        LocalDateTime now = TimeUtil.nowUtc();
        EmailVerification verification = emailVerificationRepository
                .findByEmailAndPurpose(email, purpose).orElse(null);
        if (verification != null && verification.isWithinCooldown(now, properties.resendCooldown())) {
            throw new ApiException(AuthErrorCode.VERIFICATION_RESEND_COOLDOWN);
        }

        String rawCode = generateCode();
        String codeHash = HashUtil.sha256Hex(rawCode);
        LocalDateTime expiresAt = now.plus(properties.ttl());
        if (verification == null) {
            verification = EmailVerification.issue(email, purpose, codeHash, expiresAt, now);
        } else {
            verification.reissue(codeHash, expiresAt, now);
        }
        emailVerificationRepository.save(verification); // 메일 발송 전에 커밋
        return rawCode;
    }

    /** 인증 코드 검증. 성공 시 verified 상태로 마킹한다. */
    public void verifyCode(String email, EmailVerificationPurpose purpose, String rawCode) {
        EmailVerification verification = emailVerificationRepository
                .findByEmailAndPurpose(email, purpose)
                .orElseThrow(() -> new ApiException(AuthErrorCode.VERIFICATION_CODE_MISMATCH));

        if (verification.isAttemptExceeded(properties.maxAttempts())) {
            throw new ApiException(AuthErrorCode.VERIFICATION_ATTEMPT_EXCEEDED);
        }
        if (verification.isExpired(TimeUtil.nowUtc())) {
            throw new ApiException(AuthErrorCode.VERIFICATION_CODE_EXPIRED);
        }
        if (!verification.matches(HashUtil.sha256Hex(rawCode))) {
            verification.increaseAttempt();
            emailVerificationRepository.save(verification); // 증가분 커밋 후 throw
            throw new ApiException(AuthErrorCode.VERIFICATION_CODE_MISMATCH);
        }
        verification.markVerified();
        emailVerificationRepository.save(verification);
    }

    /** 인증 완료된 행만 반환. 없거나 미인증이면 EMAIL_NOT_VERIFIED. */
    public EmailVerification requireVerified(String email, EmailVerificationPurpose purpose) {
        EmailVerification verification = emailVerificationRepository
                .findByEmailAndPurpose(email, purpose)
                .orElseThrow(() -> new ApiException(AuthErrorCode.EMAIL_NOT_VERIFIED));
        if (!verification.isVerified()) {
            throw new ApiException(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }
        return verification;
    }

    /** 인증 소비 — 재사용 방지. */
    public void consume(EmailVerification verification) {
        emailVerificationRepository.delete(verification);
    }

    /**
     * 해당 이메일의 인증 행을 목적 무관하게 전부 삭제한다. 탈퇴 시 호출한다.
     * 이 표는 이 서비스만 소유하므로(다른 서비스는 리포지토리를 직접 보지 않는다) 삭제 경로도 여기에 둔다.
     * consume()은 "성공한 흐름 1건"만 지우기 때문에, 코드만 받고 버린 흐름의 행(=실제 이메일)이 계속 남는다.
     * 파생 DELETE 쿼리라 호출자의 트랜잭션 안에서 불러야 한다(탈퇴는 @Transactional 안에서 호출한다).
     */
    public void deleteAllByEmail(String email) {
        emailVerificationRepository.deleteAllByEmail(email);
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(properties.codeLength());
        for (int i = 0; i < properties.codeLength(); i++) {
            sb.append(secureRandom.nextInt(10)); // 0~9, 선행 0 허용
        }
        return sb.toString();
    }
}
