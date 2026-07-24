package com.plog.domain.user.service;

import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 프리셋 변경. 인증된 유저가 마이페이지에서 아바타 프리셋을 바꾼다.
 * 로드한 엔티티를 변경만 하면 @Transactional 커밋 시 dirty checking으로 반영된다(별도 save 불필요).
 */
@Service
public class ProfileService {

    private final UserRepository userRepository;

    public ProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void changePreset(Long userId, ProfilePreset preset) {
        // 유효 토큰이 존재하지 않는 유저를 가리키면 토큰 무효로 취급(ProjectJoin/CreateService와 동일 컨벤션).
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(AuthErrorCode.INVALID_TOKEN));
        user.changeProfilePreset(preset);
    }
}
