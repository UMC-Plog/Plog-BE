package com.plog.global.api.response;

import com.plog.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProfileSuccessCode implements BaseCode {

    PROFILE_UPDATED(HttpStatus.OK, "PROFILE001", "프로필을 변경했습니다."),
    PROFILE_RETRIEVED(HttpStatus.OK, "PROFILE002", "프로필을 조회했습니다."),
    // 가입용 중복확인(AuthSuccessCode.NICKNAME_AVAILABLE = AUTH003)과 구분되는 마이페이지용 코드
    NICKNAME_AVAILABLE(HttpStatus.OK, "PROFILE003", "사용 가능한 닉네임입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
