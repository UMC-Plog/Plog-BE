package com.plog.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.plog.domain.user.dto.request.ProfileUpdateRequest;
import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// changePreset(Long, ProfilePreset)은 Task 8에서 updateProfile(Long, ProfileUpdateRequest)로 통합되었다.
// 부분 수정의 상세 시나리오는 ProfileServiceUpdateTest가 다루므로, 여기서는 원래 이 클래스가 검증하던
// 두 가지(프리셋 변경 반영, 존재하지 않는 유저 거부)만 새 API에 맞춰 유지한다.
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(userRepository);
    }

    @Test
    void updateProfileUpdatesTheUsersProfilePreset() {
        User user = User.createLocal("user@plog.com", "encoded", "홍길동", "gildong", ProfilePreset.OTTER);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        profileService.updateProfile(1L, new ProfileUpdateRequest(null, null, ProfilePreset.PANDA));

        assertThat(user.getProfilePreset()).isEqualTo(ProfilePreset.PANDA);
    }

    @Test
    void updateProfileFailsWhenUserDoesNotExist() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                profileService.updateProfile(99L, new ProfileUpdateRequest(null, null, ProfilePreset.PANDA)))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_TOKEN);
    }
}
