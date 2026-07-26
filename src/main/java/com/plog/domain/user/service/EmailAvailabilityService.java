package com.plog.domain.user.service;

import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입에 쓸 수 있는 이메일인지 판정한다. 이메일 가입과 소셜 로그인이 공유한다.
 * 계정 존재와 가입 수단을 그대로 노출하는 것은 와이어프레임(유가입자 모달) 요구에 따른 의도된 정책이다.
 */
@Service
public class EmailAvailabilityService {

    private final UserRepository userRepository;

    public EmailAvailabilityService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public void assertAvailableForSignup(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return;
        }
        // 탈퇴 유예를 먼저 본다 — 유예 중 계정은 가입 수단과 무관하게 같은 안내를 받아야 한다.
        if (user.isWithdrawn()) {
            throw new ApiException(AuthErrorCode.EMAIL_WITHDRAWAL_PENDING);
        }
        throw user.isSocialUser()
                ? new ApiException(AuthErrorCode.EMAIL_DUPLICATED_SOCIAL)
                : new ApiException(AuthErrorCode.EMAIL_DUPLICATED_LOCAL);
    }
}
