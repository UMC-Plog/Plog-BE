package com.plog.global.api.response;

import com.plog.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum EvaluationSuccessCode implements BaseCode {

    // 대상자 조회
    EVALUATION_TARGET_RETRIEVED(HttpStatus.OK, "EVAL200_1", "평가 대상 팀원 목록을 성공적으로 조회했습니다."),

    // 동료 평가
    PEER_EVALUATION_RETRIEVED(HttpStatus.OK, "EVAL200_2", "동료 평가 상세 조회 성공"),
    EVALUATION_SUBMITTED(HttpStatus.OK, "EVAL200_3", "동료 평가가 성공적으로 제출되었습니다."),
    EVALUATION_UPDATED(HttpStatus.OK, "EVAL200_4", "동료 평가 수정 성공"),

    // 셀프 피드백
    SELF_FEEDBACK_RETRIEVED(HttpStatus.OK, "EVAL200_5", "셀프 피드백 조회 성공"),
    SELF_FEEDBACK_SUBMITTED(HttpStatus.CREATED, "EVAL201_1", "자기 피드백이 성공적으로 등록되었습니다."),
    SELF_FEEDBACK_UPDATED(HttpStatus.OK, "EVAL200_6", "셀프 피드백 수정 성공");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}