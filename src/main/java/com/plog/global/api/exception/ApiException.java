package com.plog.global.api.exception;

import com.plog.global.api.code.BaseErrorCode;
import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final BaseErrorCode errorCode;

    public ApiException(BaseErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ApiException(BaseErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
