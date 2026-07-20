package com.plog.global.api.response;

import com.plog.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatSuccessCode implements BaseCode {

    CHANNEL_LIST_RETRIEVED(HttpStatus.OK, "CHAT001", "통합 채널 목록을 조회했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
