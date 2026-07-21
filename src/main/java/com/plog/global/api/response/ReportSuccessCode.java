package com.plog.global.api.response;

import com.plog.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReportSuccessCode implements BaseCode {

    REPORT_SEARCHED(HttpStatus.OK, "REPORT001", "리포트 검색 결과를 조회했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
