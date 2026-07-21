package com.plog.global.api.error;

import com.plog.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReportErrorCode implements BaseErrorCode {

    INVALID_SEARCH_KEYWORD(HttpStatus.BAD_REQUEST, "REPORT001", "검색어가 올바르지 않습니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "REPORT002", "조회 시작일은 종료일 이후일 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
