package com.plog.domain.project.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.project.controller.docs.ProjectRoleControllerDoc;
import io.swagger.v3.oas.annotations.Operation;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ProjectRoleControllerDocTest {

    @Test
    void keepsSwaggerDocumentationInTheDocInterface() throws Exception {
        assertThat(ProjectRoleController.class.getInterfaces())
                .contains(ProjectRoleControllerDoc.class);

        Method method = ProjectRoleControllerDoc.class.getDeclaredMethod(
                "delegateRole",
                Long.class,
                Long.class,
                Long.class,
                com.plog.domain.project.dto.request.ProjectRoleDelegationRequest.class
        );

        assertThat(method.getAnnotation(Operation.class).summary())
                .isEqualTo("프로젝트 방장 권한 위임");
    }
}
