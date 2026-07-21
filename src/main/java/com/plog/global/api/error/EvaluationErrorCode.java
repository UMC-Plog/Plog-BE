package com.plog.global.api.error;

import com.plog.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum EvaluationErrorCode implements BaseErrorCode {

    // 평가 기본 오류
    NOT_EVALUATING_STATE(HttpStatus.BAD_REQUEST, "EVAL400_1", "평가 대기 상태인 프로젝트가 아닙니다."),
    EVALUATION_NOT_FOUND(HttpStatus.BAD_REQUEST, "EVAL400_2", "해당 팀원에게 작성한 평가 내역이 존재하지 않습니다."),
    SELF_FEEDBACK_NOT_FOUND(HttpStatus.BAD_REQUEST, "EVAL400_3", "해당 프로젝트에 등록된 셀프 피드백이 존재하지 않습니다."),

    // 유효성 검사 오류
    INVALID_SCORE_RANGE(HttpStatus.BAD_REQUEST, "EVAL400_4", "평가 점수는 1~5점 사이여야 합니다."),
    KEYWORDS_REQUIRED(HttpStatus.BAD_REQUEST, "EVAL400_5", "최소 하나의 키워드를 선택해주세요."),
    FEEDBACK_REQUIRED(HttpStatus.BAD_REQUEST, "EVAL400_6", "상세 피드백은 필수 입력 항목입니다."),
    SELF_FEEDBACK_CONTENT_REQUIRED(HttpStatus.BAD_REQUEST, "EVAL400_7", "셀프 피드백 내용(content)을 입력해야 합니다."),

    // 타임아웃 및 상태 변경 오류
    CANNOT_MODIFY_EVALUATION_AFTER_PUBLISH(HttpStatus.BAD_REQUEST, "EVAL400_8", "이미 최종 리포트가 발행되어 평가를 수정할 수 없습니다."),
    CANNOT_MODIFY_FEEDBACK_AFTER_PUBLISH(HttpStatus.BAD_REQUEST, "EVAL400_9", "이미 최종 리포트가 발행되어 피드백을 수정할 수 없습니다."),

    // 팀원 간 정책
    ALREADY_EVALUATED(HttpStatus.CONFLICT, "EVAL409_1", "이미 해당 팀원을 평가하셨습니다."),
    CANNOT_EVALUATE_SELF(HttpStatus.FORBIDDEN, "EVAL403_1", "본인은 평가할 수 없습니다."),

    // 셀프 피드백 중복 등록 방지
    ALREADY_SUBMITTED_SELF_FEEDBACK(HttpStatus.CONFLICT, "EVAL409_2", "이미 셀프 피드백을 등록하셨습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}