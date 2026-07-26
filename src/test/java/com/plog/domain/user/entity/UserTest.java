package com.plog.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 25, 12, 0);

    private User localUser() {
        return User.createLocal("a@plog.test", "encoded", "홍길동", "바나나", ProfilePreset.OTTER);
    }

    @Test
    void createLocalStoresChosenProfilePreset() {
        User user = User.createLocal("user@plog.com", "encoded", "홍길동", "gildong", ProfilePreset.OTTER);

        assertThat(user.getProfilePreset()).isEqualTo(ProfilePreset.OTTER);
    }

    @Test
    void createLocalLeavesProfilePresetNullWhenNotChosen() {
        User user = User.createLocal("user@plog.com", "encoded", "홍길동", "gildong", null);

        assertThat(user.getProfilePreset()).isNull();
    }

    @Test
    void changeProfilePresetReplacesTheCurrentPreset() {
        User user = User.createLocal("user@plog.com", "encoded", "홍길동", "gildong", null);

        user.changeProfilePreset(ProfilePreset.PANDA);

        assertThat(user.getProfilePreset()).isEqualTo(ProfilePreset.PANDA);
    }

    @Test
    @DisplayName("가입 직후에는 실명 변경 권리가 남아 있다")
    void nameChangeAvailableAfterSignup() {
        assertThat(localUser().isNameChangeAvailable()).isTrue();
    }

    @Test
    @DisplayName("실명을 한 번 바꾸면 권리가 소모된다")
    void nameChangeConsumesRight() {
        User user = localUser();

        user.changeName("김철수", NOW);

        assertThat(user.getName()).isEqualTo("김철수");
        assertThat(user.getNameChangedAt()).isEqualTo(NOW);
        assertThat(user.isNameChangeAvailable()).isFalse();
    }

    @Test
    @DisplayName("탈퇴하면 deletedAt이 기록되고 탈퇴 상태가 된다")
    void withdrawMarksDeleted() {
        User user = localUser();

        user.withdraw(NOW);

        assertThat(user.isWithdrawn()).isTrue();
        assertThat(user.getDeletedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("로컬 유저 익명화는 개인정보를 파기하고 password를 임의 해시로 덮는다")
    void anonymizeLocalUser() {
        User user = localUser();
        user.withdraw(NOW);

        user.anonymize("random-encoded", "0123456789ab", NOW.plusDays(7));

        assertThat(user.getEmail()).endsWith("@deleted.plog");
        assertThat(user.getEmail()).doesNotContain("a@plog.test");
        assertThat(user.getName()).startsWith("탈퇴한사용자");
        assertThat(user.getNickname()).startsWith("탈퇴한사용자");
        // 임의값을 섞지 않으면 살아있는 유저가 "탈퇴한사용자-{id}" / "withdrawn-{id}@deleted.plog"를
        // 미리 선점해 파기 시점의 유니크 위반으로 파기를 영구히 막을 수 있다.
        assertThat(user.getEmail()).contains("0123456789ab");
        assertThat(user.getName()).endsWith("0123456789ab");
        assertThat(user.getNickname()).endsWith("0123456789ab");
        assertThat(user.getPassword()).isEqualTo("random-encoded");
        assertThat(user.getAnonymizedAt()).isEqualTo(NOW.plusDays(7));
    }

    @Test
    @DisplayName("소셜 유저 익명화는 password를 건드리지 않고 providerId만 덮는다")
    void anonymizeSocialUser() {
        User user = User.createSocial("s@plog.test", "홍길동", "바나나",
                ProviderType.GOOGLE, "google-12345");
        user.withdraw(NOW);

        user.anonymize("random-encoded", "0123456789ab", NOW.plusDays(7));

        assertThat(user.getPassword()).isNull();
        assertThat(user.getProviderType()).isEqualTo(ProviderType.GOOGLE);
        assertThat(user.getProviderId()).doesNotContain("google-12345");
        // providerId도 (provider_type, provider_id) 유니크 자리를 비워야 하므로 같은 임의값을 섞는다.
        assertThat(user.getProviderId()).endsWith("0123456789ab");
    }

    @Test
    @DisplayName("소셜 가입은 프리셋을 함께 지정할 수 있다")
    void createSocialWithPreset() {
        User user = User.createSocial("s@plog.test", "홍길동", "바나나",
                ProviderType.KAKAO, "kakao-123", ProfilePreset.PENGUIN);

        assertThat(user.getProfilePreset()).isEqualTo(ProfilePreset.PENGUIN);
        assertThat(user.getPassword()).isNull();
        assertThat(user.getProviderType()).isEqualTo(ProviderType.KAKAO);
    }

    @Test
    @DisplayName("프리셋 없이 소셜 가입하면 기본 아바타(null)로 남는다")
    void createSocialWithoutPreset() {
        User user = User.createSocial("s@plog.test", "홍길동", "바나나",
                ProviderType.GOOGLE, "google-1");

        assertThat(user.getProfilePreset()).isNull();
    }
}
