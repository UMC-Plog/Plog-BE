package com.plog.domain.project.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.project.dto.request.ProjectJoinRequest;
import com.plog.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ProjectJoinControllerOpenApiTest {

    @Test
    void documentsSuccessAndEveryClientErrorResponse() throws Exception {
        Method joinProject = ProjectJoinController.class.getDeclaredMethod(
                "joinProject",
                Long.class,
                ProjectJoinRequest.class
        );

        ApiResponses responses = joinProject.getAnnotation(ApiResponses.class);

        assertThat(responses).isNotNull();
        assertThat(responses.value())
                .extracting(response -> response.responseCode())
                .containsExactly("200", "400", "401", "409");

        Arrays.stream(responses.value())
                .filter(response -> !response.responseCode().equals("200"))
                .forEach(response -> {
                    Content[] content = response.content();
                    assertThat(content).hasSize(1);
                    assertThat(content[0].mediaType()).isEqualTo("application/json");
                    assertThat(content[0].schema().implementation()).isEqualTo(ApiResponse.class);
                });
    }
}
