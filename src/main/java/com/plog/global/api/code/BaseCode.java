package com.plog.global.api.code;

import org.springframework.http.HttpStatus;

public interface BaseCode {

    HttpStatus getHttpStatus();

    String getCode();

    String getMessage();
}
