package com.plog.global.api.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectSuccessCodeTest {

    @Test
    void usesSequentialProjectDomainCodes() {
        assertThat(List.of(
                ProjectSuccessCode.EXTERNAL_LINKS_RETRIEVED,
                ProjectSuccessCode.PROJECT_SETTINGS_RETRIEVED,
                ProjectSuccessCode.PROJECT_SETTINGS_UPDATED,
                ProjectSuccessCode.PROJECT_CREATED,
                ProjectSuccessCode.PROJECT_JOINED,
                ProjectSuccessCode.PROJECT_INVITE_REISSUED,
                ProjectSuccessCode.PROJECT_LIST_RETRIEVED,
                ProjectSuccessCode.PROJECT_INVITATION_PREVIEW_RETRIEVED
        ))
                .extracting(ProjectSuccessCode::getCode)
                .containsExactly(
                        "PROJECT001",
                        "PROJECT002",
                        "PROJECT003",
                        "PROJECT004",
                        "PROJECT005",
                        "PROJECT006",
                        "PROJECT007",
                        "PROJECT008"
                );

        assertThat(ProjectSuccessCode.PROJECT_CREATED.getHttpStatus().value()).isEqualTo(201);
        assertThat(ProjectSuccessCode.PROJECT_CREATED.getMessage()).isEqualTo("프로젝트를 생성했습니다.");
        assertThat(ProjectSuccessCode.PROJECT_JOINED.getHttpStatus().value()).isEqualTo(200);
        assertThat(ProjectSuccessCode.PROJECT_JOINED.getMessage()).isEqualTo("프로젝트에 참여했습니다.");
        assertThat(ProjectSuccessCode.PROJECT_INVITE_REISSUED.getHttpStatus().value()).isEqualTo(200);
        assertThat(ProjectSuccessCode.PROJECT_INVITE_REISSUED.getMessage())
                .isEqualTo("프로젝트 초대 링크를 재발급했습니다.");
        assertThat(ProjectSuccessCode.PROJECT_LIST_RETRIEVED.getHttpStatus().value()).isEqualTo(200);
        assertThat(ProjectSuccessCode.PROJECT_INVITATION_PREVIEW_RETRIEVED.getHttpStatus().value())
                .isEqualTo(200);
    }
}
