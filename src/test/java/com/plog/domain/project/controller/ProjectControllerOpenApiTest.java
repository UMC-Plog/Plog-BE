package com.plog.domain.project.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.project.controller.docs.ProjectControllerDoc;
import com.plog.domain.project.dto.request.ProjectCreateRequest;
import com.plog.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ProjectControllerOpenApiTest {

    @Test
    void documentsTheCreatedAndClientErrorResponseCodes() throws Exception {
        Method createProject = ProjectController.class.getDeclaredMethod(
                "createProject",
                Long.class,
                ProjectCreateRequest.class
        );

        Method documentedCreateProject = ProjectControllerDoc.class.getMethod(
                "createProject",
                Long.class,
                ProjectCreateRequest.class
        );

        ApiResponses responses = documentedCreateProject.getAnnotation(ApiResponses.class);

        assertThat(responses).isNotNull();
        assertThat(responses.value())
                .extracting(response -> response.responseCode())
                .containsExactly("201", "400", "401");

        Arrays.stream(responses.value())
                .filter(response -> response.responseCode().equals("400")
                        || response.responseCode().equals("401"))
                .forEach(response -> {
                    Content[] content = response.content();
                    assertThat(content).hasSize(1);
                    assertThat(content[0].mediaType()).isEqualTo("application/json");
                    assertThat(content[0].schema().implementation()).isEqualTo(ApiResponse.class);
                });
        assertThat(createProject.getDeclaredAnnotation(ApiResponses.class)).isNull();
    }
}
