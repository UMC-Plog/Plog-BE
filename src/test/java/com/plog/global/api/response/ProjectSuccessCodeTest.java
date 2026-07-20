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
                ProjectSuccessCode.PROJECT_CREATED
        ))
                .extracting(ProjectSuccessCode::getCode)
                .containsExactly("PROJECT001", "PROJECT002", "PROJECT003", "PROJECT004");

        assertThat(ProjectSuccessCode.PROJECT_CREATED.getHttpStatus().value()).isEqualTo(201);
        assertThat(ProjectSuccessCode.PROJECT_CREATED.getMessage()).isEqualTo("프로젝트를 생성했습니다.");
    }
}
