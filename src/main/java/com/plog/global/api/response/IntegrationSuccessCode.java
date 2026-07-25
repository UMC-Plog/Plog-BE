package com.plog.global.api.response;

import com.plog.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum IntegrationSuccessCode implements BaseCode {

    AUTHORIZATION_URL_ISSUED(HttpStatus.OK, "INTEGRATION001", "외부 연동 인증 URL을 발급했습니다."),
    INTEGRATION_CONNECTED(HttpStatus.OK, "INTEGRATION002", "외부 연동을 완료했습니다."),
    INTEGRATION_DISCONNECTED(HttpStatus.OK, "INTEGRATION003", "외부 연동을 해제했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
