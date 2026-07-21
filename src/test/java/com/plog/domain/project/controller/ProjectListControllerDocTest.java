package com.plog.domain.project.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.project.controller.docs.ProjectListControllerDoc;
import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.Test;

class ProjectListControllerDocTest {

    @Test
    void keepsOpenApiDocumentationInTheControllerDocInterface() throws Exception {
        assertThat(ProjectListController.class.getInterfaces())
                .contains(ProjectListControllerDoc.class);
        assertThat(ProjectListControllerDoc.class.getMethod(
                "getProjects",
                Long.class,
                com.plog.domain.project.entity.ProjectStatus.class,
                int.class,
                int.class
        ).getAnnotation(Operation.class)).isNotNull();
        assertThat(ProjectListController.class.getMethod(
                "getProjects",
                Long.class,
                com.plog.domain.project.entity.ProjectStatus.class,
                int.class,
                int.class
        ).getDeclaredAnnotation(Operation.class)).isNull();
    }
}
