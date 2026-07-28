package com.plog.domain.post.exception;

import com.plog.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PostErrorCode implements BaseErrorCode {
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "게시글을 찾을 수 없습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다."),
    POST_UPDATE_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "POST_UPDATE_PERMISSION_DENIED", "게시글 수정 권한이 없습니다."),
    POST_DELETE_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "POST_DELETE_PERMISSION_DENIED", "게시글 삭제 권한이 없습니다."),
    COMMENT_DELETE_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "COMMENT_DELETE_PERMISSION_DENIED", "댓글 삭제 권한이 없습니다."),
    NOTICE_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "NOTICE_PERMISSION_DENIED", "공지 변경 권한이 없습니다."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값이 올바르지 않습니다."),
    INVALID_LINK_URL(HttpStatus.BAD_REQUEST, "INVALID_LINK_URL", "공개 HTTPS 링크만 첨부할 수 있습니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "커서 형식이 올바르지 않습니다."),
    // DB 에 행이 없는 경우와 경로의 projectId 가 첨부의 프로젝트와 다른 경우를 합친다.
    // 나누면 "그 id 의 첨부가 다른 프로젝트에 존재한다"가 샌다.
    POST_ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_ATTACHMENT_NOT_FOUND",
            "첨부파일을 찾을 수 없습니다."),
    INVALID_ATTACHMENT_TYPE(HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT_TYPE",
            "링크 첨부는 다운로드 URL 을 발급할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
