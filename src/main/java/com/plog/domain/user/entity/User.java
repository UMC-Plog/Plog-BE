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
// 생성 경로를 정적 팩토리(createLocal/createSocial)로만 봉인.
// 외부에서 password와 provider를 동시에 세팅한 불법 상태를 컴파일 시점에 차단(@Check에 의존하지 않음)
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 로그인 방식은 "정확히 하나": 이메일 유저(password만) XOR 소셜 유저(provider만).
// 인증 정보가 전부 빈 row, 둘 다 가진 하이브리드 row를 스키마 차원에서 차단
@Check(constraints = "(password IS NOT NULL AND provider_type IS NULL AND provider_id IS NULL)"
        + " OR (password IS NULL AND provider_type IS NOT NULL AND provider_id IS NOT NULL)")
@Table(name = "tb_user", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_email", columnNames = "email"),
        // PostgreSQL은 NULL을 서로 다른 값으로 취급 → 이메일 유저(둘 다 NULL)끼리 충돌 없음
        @UniqueConstraint(name = "uk_user_provider", columnNames = {"provider_type", "provider_id"}),
        // 닉네임 중복확인 API의 최종 방어선(TOCTOU 대비). 가입 시 위반 예외를 잡아 에러코드로 변환
        @UniqueConstraint(name = "uk_user_nickname", columnNames = "nickname")
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

    // 프리셋 아바타. null = 기본(회색) 아바타. 커스텀 업로드는 없음(프론트가 프리셋 이미지 소유).
    @Enumerated(EnumType.STRING)
    @Column(name = "profile_preset")
    private ProfilePreset profilePreset;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type")
    private ProviderType providerType;

    @Column(name = "provider_id")
    private String providerId;

    // 프리셋 미선택(기본 아바타) 가입.
    public static User createLocal(String email, String encodedPassword, String name, String nickname) {
        return createLocal(email, encodedPassword, name, nickname, null);
    }

    // 이메일 가입: password만 세팅, provider 접근 불가 → XOR 제약을 애초에 위반할 수 없음
    // profilePreset은 선택(null=기본 아바타).
    public static User createLocal(String email, String encodedPassword, String name, String nickname,
                                   ProfilePreset profilePreset) {
        return User.builder()
                .email(email)
                .password(encodedPassword)
                .name(name)
                .nickname(nickname)
                .profilePreset(profilePreset)
                .build();
    }

    // 소셜 가입: provider만 세팅, password 접근 불가
    public static User createSocial(String email, String name, String nickname,
                                    ProviderType providerType, String providerId) {
        return User.builder()
                .email(email)
                .name(name)
                .nickname(nickname)
                .providerType(providerType)
                .providerId(providerId)
                .build();
    }

    // 로그인 방식은 provider 존재 여부로 유도 (별도 login_type 컬럼 불필요 — is_linked와 같은 원칙)
    public boolean isSocialUser() {
        return providerType != null;
    }

    // 프리셋 아바타 변경. 변경 API는 8종 중 하나만 전달(기획상 "기본으로 되돌림" 옵션 없음).
    public void changeProfilePreset(ProfilePreset profilePreset) {
        this.profilePreset = profilePreset;
    }
}
