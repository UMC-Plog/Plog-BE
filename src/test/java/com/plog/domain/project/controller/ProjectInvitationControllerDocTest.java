package com.plog.domain.project.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.project.controller.docs.ProjectInvitationControllerDoc;
import io.swagger.v3.oas.annotations.Operation;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ProjectInvitationControllerDocTest {

    @Test
    void keepsSwaggerDocumentationOutsideTheController() throws NoSuchMethodException {
        assertThat(ProjectInvitationControllerDoc.class.isAssignableFrom(
                ProjectInvitationController.class)).isTrue();

        Method method = ProjectInvitationControllerDoc.class.getDeclaredMethod(
                "getInvitationPreview",
                Long.class,
                String.class
        );

        assertThat(method.getAnnotation(Operation.class).summary())
                .isEqualTo("초대 코드 프로젝트 미리보기");
    }
}
