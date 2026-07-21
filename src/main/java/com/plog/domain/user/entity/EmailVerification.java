package com.plog.domain.user.entity;

import com.plog.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이메일 인증 코드. 이메일당 1행(재전송 시 갱신). 코드 원문이 아니라 SHA-256 해시를 저장한다.
 * 시도 횟수 제한(무차별 대입 차단), 재전송 쿨다운(메일 폭탄 차단), 인증 상태 바인딩을 담는다.
 */
@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "email_verification", uniqueConstraints = {
        @UniqueConstraint(name = "uk_email_verification_email", columnNames = "email")
})
public class EmailVerification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "email_verification_id")
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "last_sent_at", nullable = false)
    private LocalDateTime lastSentAt;

    public static EmailVerification issue(String email, String codeHash,
                                          LocalDateTime expiresAt, LocalDateTime sentAt) {
        return EmailVerification.builder()
                .email(email)
                .codeHash(codeHash)
                .expiresAt(expiresAt)
                .attemptCount(0)
                .verified(false)
                .lastSentAt(sentAt)
                .build();
    }

    /** 재전송: 새 코드로 교체하고 시도횟수·인증상태 초기화. */
    public void reissue(String codeHash, LocalDateTime expiresAt, LocalDateTime sentAt) {
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.attemptCount = 0;
        this.verified = false;
        this.lastSentAt = sentAt;
    }

    public boolean isWithinCooldown(LocalDateTime now, java.time.Duration cooldown) {
        return lastSentAt.plus(cooldown).isAfter(now);
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now);
    }

    public boolean isAttemptExceeded(int maxAttempts) {
        return attemptCount >= maxAttempts;
    }

    public void increaseAttempt() {
        this.attemptCount++;
    }

    public boolean matches(String codeHash) {
        return this.codeHash.equals(codeHash);
    }

    public void markVerified() {
        this.verified = true;
    }
}
