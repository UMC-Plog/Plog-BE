package com.plog.domain.user.service;

import com.plog.global.api.code.ErrorCode;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * tb_user 유니크 제약 위반을 에러코드로 옮긴다. 중복확인과 저장 사이의 선점(TOCTOU)이
 * 500이 아니라 의미 있는 응답으로 나가게 하는 최종 방어선이다.
 * 원인을 알 수 없는 위반까지 특정 코드로 단정하지 않도록 폴백을 남긴다.
 */
public final class UniqueViolationMapper {

    private UniqueViolationMapper() {
    }

    public static ApiException map(DataIntegrityViolationException e) {
        String constraint = (e.getCause() instanceof ConstraintViolationException cve)
                ? cve.getConstraintName() : null;
        if (constraint != null) {
            if (constraint.equalsIgnoreCase("uk_user_nickname")) {
                return new ApiException(AuthErrorCode.NICKNAME_DUPLICATED);
            }
            if (constraint.equalsIgnoreCase("uk_user_email")) {
                return new ApiException(AuthErrorCode.EMAIL_DUPLICATED_LOCAL);
            }
            if (constraint.equalsIgnoreCase("uk_user_provider")) {
                // 이메일이 아니라 (provider_type, provider_id) 충돌이다 — 같은 소셜 계정의 중복 가입.
                return new ApiException(AuthErrorCode.SOCIAL_ACCOUNT_ALREADY_REGISTERED);
            }
        }
        return new ApiException(ErrorCode.CONFLICT);
    }
}
