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
    MISSING_CLIENT_MESSAGE_ID(HttpStatus.BAD_REQUEST, "CHAT005", "clientMessageId는 필수입니다."),
    INVALID_CLIENT_MESSAGE_ID(HttpStatus.BAD_REQUEST, "CHAT006", "clientMessageId는 64자를 초과할 수 없습니다."),
    CHAT_MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT007", "존재하지 않거나 해당 채팅방 소속이 아닌 메시지입니다."),
    TOO_MANY_CHAT_ATTACHMENTS(HttpStatus.BAD_REQUEST, "CHAT008", "첨부파일은 최대 10개까지 등록할 수 있습니다."),
    INVALID_CHAT_ATTACHMENT(HttpStatus.BAD_REQUEST, "CHAT009", "첨부파일 정보가 올바르지 않습니다."),
    // DB 에 행이 없는 경우와 S3 에 객체가 없는 경우를 합친다. 클라이언트가 할 수 있는 일이
    // 같고, 구분하면 "이 첨부는 존재는 한다"를 알려주는 셈이 된다.
    CHAT_ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT010", "존재하지 않는 첨부파일입니다."),
    /**
     * 썸네일이 아직 만들어지지 않았거나(PENDING) 생성에 실패한(FAILED) 경우.
     * 정상 흐름에서는 프론트가 thumbnailUrl 이 null 이면 이 URL 을 부르지 않으므로
     * 오래된 응답을 쥔 클라이언트에 대한 방어다.
     */
    CHAT_THUMBNAIL_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT011", "썸네일이 준비되지 않았습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
