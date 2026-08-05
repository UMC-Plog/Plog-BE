package com.plog.global.api.response;

import com.plog.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReportSuccessCode implements BaseCode {

    REPORTS_RETRIEVED(HttpStatus.OK, "REPORT001", "리포트 목록을 조회했습니다."),
    REPORT_SEARCHED(HttpStatus.OK, "REPORT002", "리포트 검색 결과를 조회했습니다."),
    REPORT_DETAIL_RETRIEVED(HttpStatus.OK, "REPORT003", "리포트 상세를 조회했습니다."),
    REPORT_MEMBER_RESULT_RETRIEVED(HttpStatus.OK, "REPORT004", "리포트 멤버 결과를 조회했습니다."),
    REPORT_GENERATION_ACCEPTED(HttpStatus.ACCEPTED, "REPORT005", "리포트 생성을 시작했습니다."),
    REPORT_PDF_DOWNLOAD_URL_ISSUED(
            HttpStatus.OK,
            "REPORT006",
            "리포트 PDF 다운로드 URL을 발급했습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
