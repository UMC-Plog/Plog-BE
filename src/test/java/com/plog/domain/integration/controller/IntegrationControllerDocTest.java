package com.plog.domain.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.integration.controller.docs.IntegrationControllerDoc;
import com.plog.domain.integration.dto.request.IntegrationActorMappingRequest;
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
        assertThat(IntegrationControllerDoc.class.getMethod(
                "getActorMappings",
                Long.class,
                String.class,
                Long.class
        ).getAnnotation(Operation.class).tags())
                .containsExactly("Integration 5. 팀원 계정 매핑");
        assertThat(IntegrationControllerDoc.class.getMethod(
                "saveMyActorMapping",
                Long.class,
                String.class,
                IntegrationActorMappingRequest.class,
                Long.class
        ).getAnnotation(Operation.class)).isNotNull();
        assertThat(IntegrationControllerDoc.class.getMethod(
                "integrationCallback",
                String.class,
                String.class,
                String.class,
                String.class
        ).getAnnotation(Operation.class).hidden()).isTrue();
        assertThat(IntegrationController.class.getMethod(
                "getProjectIntegrations",
                Long.class,
                Long.class
        ).getDeclaredAnnotation(Operation.class)).isNull();
    }
}
