package com.plog.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.plog.domain.user.dto.response.ProfileResponse;
import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProfileServiceQueryTest {

    private UserRepository userRepository;
    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        profileService = new ProfileService(userRepository);
    }

    private User user() {
        return User.createLocal("a@plog.test", "encoded", "홍길동", "바나나", ProfilePreset.OTTER);
    }

    @Test
    @DisplayName("프로필 조회는 실명 변경 가능 여부를 함께 알려준다")
    void getProfileIncludesNameChangeAvailable() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user()));

        ProfileResponse response = profileService.getProfile(1L);

        assertThat(response.name()).isEqualTo("홍길동");
        assertThat(response.nickname()).isEqualTo("바나나");
        assertThat(response.profilePreset()).isEqualTo(ProfilePreset.OTTER);
        assertThat(response.nameChangeAvailable()).isTrue();
    }

    @Test
    @DisplayName("실명을 이미 바꾼 유저는 nameChangeAvailable이 false다")
    void nameChangeUnavailableAfterChange() {
        User user = user();
        user.changeName("김철수", LocalDateTime.of(2026, 7, 1, 10, 0));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThat(profileService.getProfile(1L).nameChangeAvailable()).isFalse();
    }

    @Test
    @DisplayName("본인의 현재 닉네임은 전역 중복 검사를 거치지 않고 바로 통과한다")
    void ownNicknamePasses() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user()));
        given(userRepository.existsByNickname("바나나")).willReturn(true);

        profileService.checkNicknameAvailable(1L, "바나나");

        verify(userRepository, never()).existsByNickname(anyString());
    }

    @Test
    @DisplayName("탈퇴한 계정은 프로필을 조회할 수 없다")
    void withdrawnAccountCannotReadProfile() {
        // 탈퇴 후에도 액세스 토큰이 최대 30분 살아있다(스펙상 허용). 그 창으로 죽은 계정의
        // 실명·이메일 같은 개인정보를 계속 내주면 안 된다 — 로그인/재설정과 같은 코드로 막는다.
        User withdrawn = user();
        withdrawn.withdraw(LocalDateTime.of(2026, 7, 25, 12, 0));
        given(userRepository.findById(1L)).willReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> profileService.getProfile(1L))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.WITHDRAWN_ACCOUNT);
    }

    @Test
    @DisplayName("탈퇴한 계정은 닉네임 중복확인도 할 수 없다")
    void withdrawnAccountCannotCheckNickname() {
        User withdrawn = user();
        withdrawn.withdraw(LocalDateTime.of(2026, 7, 25, 12, 0));
        given(userRepository.findById(1L)).willReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> profileService.checkNicknameAvailable(1L, "포도"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.WITHDRAWN_ACCOUNT);
    }

    @Test
    @DisplayName("다른 유저가 쓰는 닉네임은 거부한다")
    void otherNicknameRejected() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user()));
        given(userRepository.existsByNickname("포도")).willReturn(true);

        assertThatThrownBy(() -> profileService.checkNicknameAvailable(1L, "포도"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.NICKNAME_DUPLICATED);
    }
}
