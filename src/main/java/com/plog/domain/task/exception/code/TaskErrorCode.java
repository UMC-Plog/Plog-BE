package com.plog.domain.task.exception.code;

import com.plog.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TaskErrorCode implements BaseErrorCode {

    ASSIGNEE_NOT_FOUND(HttpStatus.NOT_FOUND, "TASK001", "지정한 담당자를 찾을 수 없습니다."),
    ASSIGNEE_PROJECT_MISMATCH(HttpStatus.BAD_REQUEST, "TASK002", "담당자가 해당 프로젝트 소속이 아닙니다."),
    // 나간(EXIT) 멤버는 참여 팀원이 아니므로 지정 불가
    ASSIGNEE_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "TASK003", "이미 프로젝트를 나간 팀원은 담당자로 지정할 수 없습니다."),
    // 담당 영역 선택지는 프로젝트 유형(개발/일반)에 따라 분류
    INVALID_CATEGORY_FOR_PROJECT_TYPE(HttpStatus.BAD_REQUEST, "TASK004", "프로젝트 유형에 맞지 않는 담당 영역입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}