package com.plog.global.api.exception;

import com.plog.domain.notification.controller.FcmTokenController;
import com.plog.domain.post.controller.PostController;
import com.plog.domain.project.controller.ProjectSettingsController;
import com.plog.global.api.error.AssignedApiErrorCode;
import com.plog.global.api.response.ApiResponse;
import com.plog.infrastructure.s3.FileStorageController;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        PostController.class,
        ProjectSettingsController.class,
        FcmTokenController.class,
        FileStorageController.class
})
public class AssignedApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(AssignedApiErrorCode.VALIDATION_ERROR, errors));
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest() {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(AssignedApiErrorCode.VALIDATION_ERROR));
    }
}
