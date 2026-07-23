package com.plog.global.api.response;

import com.plog.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TaskSuccessCode implements BaseCode {

    TASK_CREATED(HttpStatus.CREATED, "TASK_SUCCESS_001", "업무카드가 생성되었습니다."),
    TASK_LIST_FOUND(HttpStatus.OK, "TASK_SUCCESS_002", "업무카드 목록 조회에 성공했습니다."),
    TASK_DETAIL_FOUND(HttpStatus.OK, "TASK_SUCCESS_003", "업무카드 상세 조회에 성공했습니다."),
    TASK_UPDATED(HttpStatus.OK, "TASK_SUCCESS_004", "업무카드가 수정되었습니다."),
    TASK_DELETED(HttpStatus.OK, "TASK_SUCCESS_005", "업무카드가 삭제되었습니다."),
    TASK_STATUS_UPDATED(HttpStatus.OK, "TASK_SUCCESS_006", "업무카드 상태가 변경되었습니다."),
    TASK_ATTACHMENT_ADDED(HttpStatus.CREATED, "TASK_SUCCESS_007", "첨부파일이 등록되었습니다."),
    TASK_ATTACHMENT_DELETED(HttpStatus.OK, "TASK_SUCCESS_008", "첨부파일이 삭제되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
