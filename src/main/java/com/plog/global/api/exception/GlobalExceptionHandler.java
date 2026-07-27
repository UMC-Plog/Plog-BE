package com.plog.global.api.exception;

import com.plog.global.api.code.BaseErrorCode;
import com.plog.global.api.code.ErrorCode;
import com.plog.global.api.response.ApiResponse;
import com.plog.infrastructure.s3.FileStorageErrorCode;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
        return response(exception.getErrorCode(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage())
        );
        return response(ErrorCode.INVALID_INPUT, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation() {
        return response(ErrorCode.INVALID_INPUT, null);
    }

    /**
     * uk_{post,task,chat}_attachment_file 위반. 애플리케이션 검증을 우회한 동시 요청이
     * 여기까지 온 것이므로 5xx 가 아니라 409 로 돌려준다.
     * <p>
     * 애플리케이션 검증은 에러 메시지 품질용이고, DB 제약이 정합성 최종 방어선이다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        if (message != null && message.contains("_attachment_file")) {
            log.warn("attachment_file_unique_violation", exception);
            return response(FileStorageErrorCode.FILE_ALREADY_ATTACHED, null);
        }
        // 되던지면 catch-all(@ExceptionHandler(Exception)) 이 아니라 컨테이너로 빠져나가
        // ApiResponse 봉투 대신 /error 기본 바디가 나가고 에러 로그도 남지 않는다.
        log.error("Unhandled data integrity violation", exception);
        return response(ErrorCode.INTERNAL_SERVER_ERROR, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage() {
        return response(ErrorCode.INVALID_REQUEST, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch() {
        return response(ErrorCode.INVALID_INPUT, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed() {
        return response(ErrorCode.METHOD_NOT_ALLOWED, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound() {
        return response(ErrorCode.NOT_FOUND, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled exception", exception);
        return response(ErrorCode.INTERNAL_SERVER_ERROR, null);
    }

    private <T> ResponseEntity<ApiResponse<T>> response(BaseErrorCode errorCode, T result) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.failure(errorCode, result));
    }
}
