package com.plog.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.global.api.code.ErrorCode;
import com.plog.global.api.error.AuthErrorCode;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class UniqueViolationMapperTest {

    private DataIntegrityViolationException violation(String constraintName) {
        ConstraintViolationException cause = new ConstraintViolationException(
                "unique violation", new SQLException("unique violation"), constraintName);
        return new DataIntegrityViolationException("wrapped", cause);
    }

    @Test
    @DisplayName("닉네임 유니크 위반은 NICKNAME_DUPLICATED")
    void mapsNickname() {
        assertThat(UniqueViolationMapper.map(violation("uk_user_nickname")).getErrorCode())
                .isEqualTo(AuthErrorCode.NICKNAME_DUPLICATED);
    }

    @Test
    @DisplayName("이메일 유니크 위반은 EMAIL_DUPLICATED_LOCAL")
    void mapsEmail() {
        assertThat(UniqueViolationMapper.map(violation("uk_user_email")).getErrorCode())
                .isEqualTo(AuthErrorCode.EMAIL_DUPLICATED_LOCAL);
    }

    @Test
    @DisplayName("provider 유니크 위반은 이메일 충돌과 구분되는 SOCIAL_ACCOUNT_ALREADY_REGISTERED")
    void mapsProvider() {
        assertThat(UniqueViolationMapper.map(violation("uk_user_provider")).getErrorCode())
                .isEqualTo(AuthErrorCode.SOCIAL_ACCOUNT_ALREADY_REGISTERED);
    }

    @Test
    @DisplayName("제약명을 알 수 없으면 범용 충돌로 폴백한다")
    void fallsBackToConflict() {
        assertThat(UniqueViolationMapper.map(new DataIntegrityViolationException("no cause")).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }
}
