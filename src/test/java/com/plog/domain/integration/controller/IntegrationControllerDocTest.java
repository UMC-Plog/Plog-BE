package com.plog.domain.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.integration.controller.docs.IntegrationControllerDoc;
import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.Test;

class IntegrationControllerDocTest {

    @Test
    void keepsOpenApiDocumentationInTheControllerDocInterface() throws Exception {
        assertThat(IntegrationController.class.getInterfaces())
                .contains(IntegrationControllerDoc.class);
        assertThat(IntegrationControllerDoc.class.getMethod(
                "getProjectIntegrations",
                Long.class,
                Long.class
        ).getAnnotation(Operation.class)).isNotNull();
        assertThat(IntegrationControllerDoc.class.getMethod(
                "issueAuthorizationUrl",
                Long.class,
                String.class,
                Long.class
        ).getAnnotation(Operation.class)).isNotNull();
        assertThat(IntegrationControllerDoc.class.getMethod(
                "disconnect",
                Long.class,
                String.class,
                Long.class
        ).getAnnotation(Operation.class)).isNotNull();
        assertThat(IntegrationController.class.getMethod(
                "getProjectIntegrations",
                Long.class,
                Long.class
        ).getDeclaredAnnotation(Operation.class)).isNull();
    }
}
