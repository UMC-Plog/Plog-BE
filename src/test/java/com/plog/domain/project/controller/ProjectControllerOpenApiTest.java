package com.plog.domain.project.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.project.dto.request.ProjectCreateRequest;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ProjectControllerOpenApiTest {

    @Test
    void documentsTheCreatedAndClientErrorResponseCodes() throws Exception {
        Method createProject = ProjectController.class.getDeclaredMethod(
                "createProject",
                Long.class,
                ProjectCreateRequest.class
        );

        ApiResponses responses = createProject.getAnnotation(ApiResponses.class);

        assertThat(responses).isNotNull();
        assertThat(responses.value())
                .extracting(response -> response.responseCode())
                .containsExactly("201", "400", "401");
    }
}
