package com.plog.global.api.error;

import com.plog.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReportErrorCode implements BaseErrorCode {

    INVALID_SEARCH_KEYWORD(HttpStatus.BAD_REQUEST, "REPORT001", "검색어가 올바르지 않습니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "REPORT002", "조회 시작일은 종료일 이후일 수 없습니다."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "REPORT003", "리포트를 찾을 수 없습니다."),
    REPORT_NOT_COMPLETED(HttpStatus.CONFLICT, "REPORT004", "완료된 리포트만 다운로드할 수 있습니다."),
    REPORT_PDF_NOT_FOUND(HttpStatus.NOT_FOUND, "REPORT005", "리포트 PDF를 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
