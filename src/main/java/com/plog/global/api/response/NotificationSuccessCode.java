package com.plog.global.api.response;

import com.plog.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationSuccessCode implements BaseCode {

    NOTIFICATION_LIST_RETRIEVED(HttpStatus.OK, "NOTI001", "알림 목록을 조회했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}