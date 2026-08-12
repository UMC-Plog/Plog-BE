package com.plog.domain.user.entity;

import com.plog.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리프레시 토큰. User 1:N (다중 기기 = 기기마다 row). Redis 대신 PostgreSQL 저장.
 * 원문이 아니라 SHA-256 해시를 저장한다(BCrypt 아님 — 유니크 인덱스 조회가 가능해야 하므로).
 */
@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "refresh_token", uniqueConstraints = {
        @UniqueConstraint(name = "uk_refresh_token_hash", columnNames = "token_hash")
})
public class RefreshToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * 회전으로 소모된 시각. null이면 아직 안 쓴 토큰이다.
     * <p>
     * 예전엔 회전이 곧 DELETE 였다. 그러면 재발급이 한 번만 겹쳐도 — PWA 콜드 스타트의 중복 호출,
     * 모바일 네트워크의 재시도 — 나머지가 전부 실패해 로그아웃됐다. 지우는 대신 시각을 남겨서
     * "방금 쓴 토큰의 재시도"와 "한참 뒤에 나타난 탈취 의심"을 구분한다.
     */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    public static RefreshToken issue(User user, String tokenHash, LocalDateTime expiresAt) {
        return RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .build();
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now);
    }
}
