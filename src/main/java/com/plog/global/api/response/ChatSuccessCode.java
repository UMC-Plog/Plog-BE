package com.plog.global.api.response;

import com.plog.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatSuccessCode implements BaseCode {

    CHANNEL_LIST_RETRIEVED(HttpStatus.OK, "CHAT001", "통합 채널 목록을 조회했습니다."),
    CHANNEL_SEARCH_RETRIEVED(HttpStatus.OK, "CHAT002", "채팅방 검색 결과를 조회했습니다."),
    CHAT_ROOM_READ_UPDATED(HttpStatus.OK, "CHAT003", "채팅방 읽음 상태를 갱신했습니다."),
    MESSAGE_LIST_RETRIEVED(HttpStatus.OK, "CHAT004", "채팅 메시지 목록을 조회했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
