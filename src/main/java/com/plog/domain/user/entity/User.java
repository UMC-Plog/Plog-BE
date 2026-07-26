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

    // 실명은 계정당 1회만 변경 가능(분석 리포트 신뢰도). null = 아직 변경하지 않음.
    @Column(name = "name_changed_at")
    private LocalDateTime nameChangedAt;

    // 탈퇴 후 유예기간이 지나 개인정보를 파기한 시각. 배치가 같은 row를 반복 처리하지 않도록 하는 표식.
    @Column(name = "anonymized_at")
    private LocalDateTime anonymizedAt;

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

    public boolean isNameChangeAvailable() {
        return nameChangedAt == null;
    }

    /** 실명 변경. 1회 제한 검사는 서비스가 isNameChangeAvailable()로 수행한다(EmailVerification 컨벤션). */
    public void changeName(String name, LocalDateTime changedAt) {
        this.name = name;
        this.nameChangedAt = changedAt;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    /** 비밀번호 교체. 항상 인코딩된 해시만 받는다(평문 금지). */
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void withdraw(LocalDateTime withdrawnAt) {
        markDeleted(withdrawnAt);
    }

    public boolean isWithdrawn() {
        return getDeletedAt() != null;
    }

    /**
     * 유예기간 경과 후 개인정보 파기. 유니크 제약(uk_user_email, uk_user_nickname, uk_user_provider)
     * 자리를 비워 재가입이 가능해진다. @Check 제약(password XOR provider)을 위반하지 않도록
     * 가입 수단별로 나눈다 — 로컬은 password를 임의 해시로, 소셜은 providerId만 덮는다.
     * <p>
     * uniqueSuffix(임의값)를 반드시 섞는다. id만 쓰면 "탈퇴한사용자-42" / "withdrawn-42@deleted.plog"는
     * 살아있는 유저가 미리 선점할 수 있는 값이라(닉네임 검증은 공백만 막고, 그 이메일도 문법상 유효하다)
     * 파기 시점에 uk_user_nickname / uk_user_email 위반으로 파기가 영구히 막힐 수 있다.
     * 이 값들은 화면에 노출되지 않는다(보관된 글·채팅은 project_members.an_nickname을 쓴다) —
     * 가독성보다 충돌 불가능성이 우선이고, 추적을 위해 id는 그대로 남긴다.
     */
    public void anonymize(String encodedRandomPassword, String uniqueSuffix, LocalDateTime anonymizedAt) {
        this.email = "withdrawn-" + id + "-" + uniqueSuffix + "@deleted.plog";
        this.name = "탈퇴한사용자-" + id + "-" + uniqueSuffix;
        this.nickname = "탈퇴한사용자-" + id + "-" + uniqueSuffix;
        if (isSocialUser()) {
            this.providerId = "withdrawn-" + id + "-" + uniqueSuffix;
        } else {
            this.password = encodedRandomPassword;
        }
        this.anonymizedAt = anonymizedAt;
    }
}
