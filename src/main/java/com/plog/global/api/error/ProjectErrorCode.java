package com.plog.global.api.error;

import com.plog.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProjectErrorCode implements BaseErrorCode {

    PROJECT_NOT_FOUND(HttpStatus.BAD_REQUEST, "PROJ400_1", "존재하지 않는 프로젝트이거나 접근 권한이 없습니다."),
    MEMBER_NOT_FOUND(HttpStatus.BAD_REQUEST, "PROJ400_2", "해당 팀원은 현재 프로젝트에 참여하고 있지 않습니다."),
    INVALID_PROJECT_END_DAY(HttpStatus.BAD_REQUEST, "E400_INVALID_DATE", "예상 종료일은 오늘 날짜 이후여야 합니다."),
    OWNER_MUST_TRANSFER(HttpStatus.BAD_REQUEST, "OWNER_MUST_TRANSFER", "프로젝트 생성자는 다른 팀원에게 방장 권한을 이전해야 나갈 수 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}