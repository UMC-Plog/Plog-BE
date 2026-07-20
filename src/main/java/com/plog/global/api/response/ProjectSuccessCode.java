package com.plog.global.api.response;

import com.plog.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProjectSuccessCode implements BaseCode {

    EXTERNAL_LINKS_RETRIEVED(HttpStatus.OK, "PROJECT001", "외부 툴 연동 상태를 조회했습니다."),
    PROJECT_SETTINGS_RETRIEVED(HttpStatus.OK, "PROJECT002", "프로젝트 설정을 조회했습니다."),
    PROJECT_SETTINGS_UPDATED(HttpStatus.OK, "PROJECT003", "프로젝트 설정을 수정했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
