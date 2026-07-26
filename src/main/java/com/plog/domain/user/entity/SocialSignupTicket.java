package com.plog.domain.user.entity;

import com.plog.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 소셜 인증은 끝났지만 아직 가입이 완료되지 않은 상태를 들고 있는 일회용 티켓.
 * 콜백 시점에는 닉네임·실명·약관이 없어 User row를 만들 수 없기 때문에 필요하다.
 * <p>
 * 원문이 아니라 SHA-256 해시를 저장한다(RefreshToken과 같은 이유 — 32바이트 난수라 키 없는 해시로 충분).
 * 소비 상태 전이는 엔티티가 아니라 리포지토리의 조건부 UPDATE로 한다:
 * 읽고-바꿔-저장하면 동시 요청 둘이 같은 티켓으로 함께 통과할 수 있다.
 */
@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "social_signup_ticket", uniqueConstraints = {
        @UniqueConstraint(name = "uk_social_signup_ticket_hash", columnNames = "ticket_hash")
})
public class SocialSignupTicket extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "social_signup_ticket_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false)
    private ProviderType providerType;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(nullable = false)
    private String email;

    @Column(name = "ticket_hash", nullable = false)
    private String ticketHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    public static SocialSignupTicket issue(ProviderType providerType, String providerId, String email,
                                           String ticketHash, LocalDateTime expiresAt) {
        return SocialSignupTicket.builder()
                .providerType(providerType)
                .providerId(providerId)
                .email(email)
                .ticketHash(ticketHash)
                .expiresAt(expiresAt)
                .build();
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now);
    }
}
