package com.plog.global.api.error;

import com.plog.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum EvaluationErrorCode implements BaseErrorCode {

    // 평가 기본 오류
    ALREADY_EVALUATED(HttpStatus.CONFLICT, "EVAL001", "이미 해당 팀원을 평가하셨습니다."),
    CANNOT_EVALUATE_SELF(HttpStatus.FORBIDDEN, "EVAL002", "본인은 평가할 수 없습니다."),
    EVALUATION_PERIOD_CLOSED(HttpStatus.BAD_REQUEST, "EVAL003", "평가 기간이 종료되었습니다."),
    EVALUATION_NOT_STARTED(HttpStatus.BAD_REQUEST, "EVAL004", "평가 기간이 아닙니다."),
    EVALUATION_NOT_FOUND(HttpStatus.BAD_REQUEST, "EVAL010", "해당 팀원에게 작성한 평가 내역이 존재하지 않습니다."),

    // 유효성 검사 오류
    INVALID_SCORE_RANGE(HttpStatus.BAD_REQUEST, "EVAL005", "평가 점수는 1~5점 사이여야 합니다."),
    FEEDBACK_REQUIRED(HttpStatus.BAD_REQUEST, "EVAL006", "상세 피드백은 필수 입력 항목입니다."),
    KEYWORDS_REQUIRED(HttpStatus.BAD_REQUEST, "EVAL007", "최소 하나의 핵심 키워드를 선택해주세요."),

    // 타임아웃 및 상태 오류
    CANNOT_MODIFY_AFTER_PUBLISH(HttpStatus.CONFLICT, "EVAL008", "리포트가 발행되어 평가를 수정할 수 없습니다."),

    // 셀프 피드백
    SELF_FEEDBACK_NOT_FOUND(HttpStatus.NOT_FOUND, "EVAL009", "해당 프로젝트에 등록된 셀프 피드백이 존재하지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}