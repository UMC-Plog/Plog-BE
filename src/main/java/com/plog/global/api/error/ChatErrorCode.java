package com.plog.global.api.error;

import com.plog.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements BaseErrorCode {

    INVALID_SEARCH_KEYWORD(HttpStatus.BAD_REQUEST, "CHAT001", "검색어가 올바르지 않습니다."),
    FORBIDDEN_CHAT_ROOM_ACCESS(HttpStatus.FORBIDDEN, "CHAT002", "채팅방 접근 권한이 없습니다."),
    EMPTY_MESSAGE_CONTENT(HttpStatus.BAD_REQUEST, "CHAT003", "메시지 내용은 비어 있을 수 없습니다."),
    CHAT_ROOM_LOCK_TIMEOUT(HttpStatus.CONFLICT, "CHAT004", "메시지 처리 중 충돌이 발생했습니다. 잠시 후 다시 시도해주세요."),
    MISSING_CLIENT_MESSAGE_ID(HttpStatus.BAD_REQUEST, "CHAT005", "clientMessageId는 필수입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
