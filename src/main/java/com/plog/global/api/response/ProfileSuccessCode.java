package com.plog.global.api.response;

import com.plog.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProfileSuccessCode implements BaseCode {

    PROFILE_PRESET_UPDATED(HttpStatus.OK, "PROFILE001", "프로필 프리셋을 변경했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
