package com.plog.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.plog.domain.user.dto.request.ProfileUpdateRequest;
import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.error.UserErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProfileServiceUpdateTest {

    private UserRepository userRepository;
    private ProfileService profileService;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        profileService = new ProfileService(userRepository);
        user = User.createLocal("a@plog.test", "encoded", "홍길동", "바나나", ProfilePreset.OTTER);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.saveAndFlush(user)).willReturn(user);
    }

    @Test
    @DisplayName("닉네임만 보내면 닉네임만 바뀐다")
    void updatesNicknameOnly() {
        profileService.updateProfile(1L, new ProfileUpdateRequest(null, "망고", null));

        assertThat(user.getNickname()).isEqualTo("망고");
        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(user.getProfilePreset()).isEqualTo(ProfilePreset.OTTER);
    }

    @Test
    @DisplayName("프리셋만 보내면 프리셋만 바뀐다")
    void updatesPresetOnly() {
        profileService.updateProfile(1L, new ProfileUpdateRequest(null, null, ProfilePreset.PENGUIN));

        assertThat(user.getProfilePreset()).isEqualTo(ProfilePreset.PENGUIN);
        assertThat(user.getNickname()).isEqualTo("바나나");
    }

    @Test
    @DisplayName("아무 필드도 없으면 아무것도 바뀌지 않는다")
    void noOpWhenAllNull() {
        profileService.updateProfile(1L, new ProfileUpdateRequest(null, null, null));

        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(user.isNameChangeAvailable()).isTrue();
    }

    @Test
    @DisplayName("현재와 같은 실명을 보내면 1회 권리를 소모하지 않는다")
    void sameNameDoesNotConsumeRight() {
        profileService.updateProfile(1L, new ProfileUpdateRequest("홍길동", null, null));

        assertThat(user.isNameChangeAvailable()).isTrue();
    }

    @Test
    @DisplayName("현재와 다른 실명을 보내면 이름/변경일시/권리소진이 모두 반영된다")
    void nameChangeAppliesAndConsumesRight() {
        profileService.updateProfile(1L, new ProfileUpdateRequest("김철수", null, null));

        assertThat(user.getName()).isEqualTo("김철수");
        assertThat(user.getNameChangedAt()).isNotNull();
        assertThat(user.isNameChangeAvailable()).isFalse();
    }

    @Test
    @DisplayName("실명을 두 번 바꾸려 하면 거부한다")
    void secondNameChangeRejected() {
        user.changeName("김철수", LocalDateTime.of(2026, 7, 1, 10, 0));

        assertThatThrownBy(() ->
                profileService.updateProfile(1L, new ProfileUpdateRequest("이영희", null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(UserErrorCode.NAME_CHANGE_ALREADY_USED);
    }

    @Test
    @DisplayName("탈퇴한 계정은 프로필을 수정할 수 없다")
    void withdrawnAccountCannotUpdateProfile() {
        // 탈퇴 12:00 → 12:05에 남은 액세스 토큰으로 닉네임을 바꾸면, 죽은 계정이 닉네임을 7일간 선점하거나
        // 1회뿐인 실명 변경 권리를 파기 배치가 덮어쓸 행에 소모한다. 프로필은 유일하게 이 검사가 없던 계정 표면이었다.
        user.withdraw(LocalDateTime.of(2026, 7, 25, 12, 0));

        assertThatThrownBy(() ->
                profileService.updateProfile(1L, new ProfileUpdateRequest(null, "포도", null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.WITHDRAWN_ACCOUNT);

        assertThat(user.getNickname()).isEqualTo("바나나");
    }

    @Test
    @DisplayName("다른 유저가 쓰는 닉네임으로 바꾸려 하면 거부한다")
    void duplicatedNicknameRejected() {
        given(userRepository.existsByNickname("포도")).willReturn(true);

        assertThatThrownBy(() ->
                profileService.updateProfile(1L, new ProfileUpdateRequest(null, "포도", null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.NICKNAME_DUPLICATED);
    }
}
