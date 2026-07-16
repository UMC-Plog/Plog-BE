package com.plog.infrastructure.s3;

import com.plog.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FileStorageErrorCode implements BaseErrorCode {
    UNSUPPORTED_ATTACHMENT_TYPE(HttpStatus.BAD_REQUEST, "UNSUPPORTED_ATTACHMENT_TYPE", "지원하지 않는 파일 형식입니다."),
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "FILE_SIZE_EXCEEDED", "파일 크기는 50MB를 초과할 수 없습니다."),
    INVALID_FILE_KEY(HttpStatus.BAD_REQUEST, "INVALID_FILE_KEY", "업로드된 파일을 확인할 수 없습니다."),
    FILE_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_ERROR", "파일 저장소 처리에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
