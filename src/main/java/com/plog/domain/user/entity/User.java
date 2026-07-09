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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 로그인 방식은 "정확히 하나": 이메일 유저(password만) XOR 소셜 유저(provider만).
// 인증 정보가 전부 빈 row, 둘 다 가진 하이브리드 row를 스키마 차원에서 차단
@Check(constraints = "(password IS NOT NULL AND provider_type IS NULL AND provider_id IS NULL)"
        + " OR (password IS NULL AND provider_type IS NOT NULL AND provider_id IS NOT NULL)")
@Table(name = "tb_user", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_email", columnNames = "email"),
        // PostgreSQL은 NULL을 서로 다른 값으로 취급 → 이메일 유저(둘 다 NULL)끼리 충돌 없음
        @UniqueConstraint(name = "uk_user_provider", columnNames = {"provider_type", "provider_id"})
})
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false)
    private String email;

    // 이메일 로그인 유저만 값 존재 (BCrypt 해시 저장, 평문 금지)
    @Column(nullable = true)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String nickname;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type")
    private ProviderType providerType;

    @Column(name = "provider_id")
    private String providerId;

    // 로그인 방식은 provider 존재 여부로 유도 (별도 login_type 컬럼 불필요 — is_linked와 같은 원칙)
    public boolean isSocialUser() {
        return providerType != null;
    }
}
