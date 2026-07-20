package com.plog.global.api.error;

import com.plog.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProjectErrorCode implements BaseErrorCode {

    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "PROJECT001", "프로젝트를 찾을 수 없습니다."),
    PROJECT_MEMBER_REQUIRED(HttpStatus.FORBIDDEN, "PROJECT002", "활성 프로젝트 멤버만 접근할 수 있습니다."),
    PROJECT_SETTING_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "PROJECT003", "프로젝트 설정 변경 권한이 없습니다."),
    INVALID_PROJECT_NAME(HttpStatus.BAD_REQUEST, "PROJECT004", "프로젝트명은 2자 이상 20자 이하여야 합니다."),
    INVALID_PROJECT_END_DAY(HttpStatus.BAD_REQUEST, "PROJECT005", "예상 종료일은 오늘과 시작일 이후여야 합니다."),
    INVITE_TOKEN_CONFIGURATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "PROJECT006", "초대 토큰 설정이 올바르지 않습니다."),
    INVITE_TOKEN_GENERATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "PROJECT007", "초대 토큰을 발급할 수 없습니다."),
    INVALID_INVITE_CODE(HttpStatus.BAD_REQUEST, "PROJECT008", "유효하지 않은 초대 코드입니다."),
    PROJECT_ALREADY_JOINED(HttpStatus.CONFLICT, "PROJECT009", "이미 참여 중인 프로젝트입니다."),

    PROJECT_ACCESS_DENIED_OR_NOT_FOUND(HttpStatus.BAD_REQUEST, "PROJ400_1", "존재하지 않는 프로젝트이거나 접근 권한이 없습니다."),
    MEMBER_NOT_FOUND(HttpStatus.BAD_REQUEST, "PROJ400_2", "해당 팀원은 현재 프로젝트에 참여하고 있지 않습니다."),
    PROJECT_END_DAY_MUST_BE_FUTURE(HttpStatus.BAD_REQUEST, "E400_INVALID_DATE", "예상 종료일은 오늘 날짜 이후여야 합니다."),
    OWNER_MUST_TRANSFER(HttpStatus.BAD_REQUEST, "OWNER_MUST_TRANSFER", "프로젝트 생성자는 다른 팀원에게 방장 권한을 이전해야 나갈 수 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
