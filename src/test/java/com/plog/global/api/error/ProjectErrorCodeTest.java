package com.plog.global.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ProjectErrorCodeTest {

    @Test
    void usesSequentialProjectDomainCodes() {
        assertThat(List.of(
                ProjectErrorCode.PROJECT_NOT_FOUND,
                ProjectErrorCode.PROJECT_MEMBER_REQUIRED,
                ProjectErrorCode.PROJECT_SETTING_PERMISSION_DENIED,
                ProjectErrorCode.INVALID_PROJECT_NAME,
                ProjectErrorCode.INVALID_PROJECT_END_DAY,
                ProjectErrorCode.INVITE_TOKEN_CONFIGURATION_ERROR,
                ProjectErrorCode.INVITE_TOKEN_GENERATION_ERROR,
                ProjectErrorCode.INVALID_INVITE_CODE,
                ProjectErrorCode.PROJECT_ALREADY_JOINED
        ))
                .extracting(ProjectErrorCode::getCode)
                .containsExactly(
                        "PROJECT001",
                        "PROJECT002",
                        "PROJECT003",
                        "PROJECT004",
                        "PROJECT005",
                        "PROJECT006",
                        "PROJECT007",
                        "PROJECT008",
                        "PROJECT009"
                );
    }

    @Test
    void inviteTokenGenerationErrorDoesNotExposeTokenMaterial() {
        ProjectErrorCode errorCode = ProjectErrorCode.INVITE_TOKEN_GENERATION_ERROR;

        assertThat(errorCode.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(errorCode.getCode()).isEqualTo("PROJECT007");
        assertThat(errorCode.getMessage()).isEqualTo("초대 토큰을 발급할 수 없습니다.");
    }
}
