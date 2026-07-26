package com.plog.global.api.response;

import com.plog.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 계정 자체(탈퇴 등)의 성공 코드. 프로필 수정 성공은 ProfileSuccessCode 를 쓴다. */
@Getter
@RequiredArgsConstructor
public enum UserSuccessCode implements BaseCode {

    WITHDRAWAL_COMPLETED(HttpStatus.OK, "USER001", "탈퇴가 완료되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
