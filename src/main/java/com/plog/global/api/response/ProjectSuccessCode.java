package com.plog.global.api.response;

import com.plog.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProjectSuccessCode implements BaseCode {

    PROJECT_STATUS_UPDATED(HttpStatus.OK, "PROJ200_1", "프로젝트 상태를 성공적으로 확인/갱신했습니다."),
    PROJECT_SETTING_UPDATED(HttpStatus.OK, "PROJ200_2", "프로젝트 설정이 변경되었습니다."),
    PROJECT_ROLE_DELEGATED(HttpStatus.OK, "PROJ200_3", "프로젝트 방장 권한 위임 성공"),
    PROJECT_LEFT_SUCCESS(HttpStatus.OK, "PROJ200_4", "프로젝트에서 성공적으로 나갔습니다. 기존 활동 기록은 보존됩니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}