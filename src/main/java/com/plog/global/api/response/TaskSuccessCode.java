package com.plog.global.api.response;

import com.plog.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TaskSuccessCode implements BaseCode {

    TASK_CREATED(HttpStatus.CREATED, "TASK_SUCCESS_001", "업무카드가 생성되었습니다."),
    TASK_LIST_FOUND(HttpStatus.OK, "TASK_SUCCESS_002", "업무카드 목록 조회에 성공했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
