package com.plog.domain.task.exception.code;

import com.plog.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TaskSuccessCode implements BaseCode {

    TASK_CREATED(HttpStatus.CREATED, "TASK001", "업무카드가 생성되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
