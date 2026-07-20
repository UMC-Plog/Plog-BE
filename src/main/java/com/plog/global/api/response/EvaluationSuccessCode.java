package com.plog.global.api.response;

import com.plog.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum EvaluationSuccessCode implements BaseCode {

    EVALUATION_SUBMITTED(HttpStatus.CREATED, "EVAL001", "동료 평가가 성공적으로 제출되었습니다."),
    EVALUATION_UPDATED(HttpStatus.OK, "EVAL002", "동료 평가가 수정되었습니다."),
    SELF_FEEDBACK_SUBMITTED(HttpStatus.CREATED, "EVAL003", "자기 피드백이 등록되었습니다."),
    SELF_FEEDBACK_UPDATED(HttpStatus.OK, "EVAL004", "자기 피드백이 수정되었습니다."),
    EVALUATION_TARGET_RETRIEVED(HttpStatus.OK, "EVAL005", "평가 대상 목록을 성공적으로 조회했습니다."),
    PEER_EVALUATION_RETRIEVED(HttpStatus.OK, "EVAL007", "동료 평가 상세 조회 성공"),

    // 셀프 피드백
    SELF_FEEDBACK_RETRIEVED(HttpStatus.OK, "EVAL006", "셀프 피드백 조회 성공");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}