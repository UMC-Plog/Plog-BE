package com.plog.global.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.plog.global.api.code.BaseCode;
import com.plog.global.api.code.BaseErrorCode;
import com.plog.global.api.code.SuccessCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean isSuccess,
        String code,
        String message,
        T result
) {

    public static <T> ApiResponse<T> success(T result) {
        return success(SuccessCode.OK, result);
    }

    public static <T> ApiResponse<T> success(BaseCode code, T result) {
        return new ApiResponse<>(true, code.getCode(), code.getMessage(), result);
    }

    public static <T> ApiResponse<T> failure(BaseErrorCode code) {
        return failure(code, null);
    }

    public static <T> ApiResponse<T> failure(BaseErrorCode code, T result) {
        return new ApiResponse<>(false, code.getCode(), code.getMessage(), result);
    }
}
