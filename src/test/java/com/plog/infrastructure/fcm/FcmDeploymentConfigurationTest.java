package com.plog.infrastructure.fcm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FcmDeploymentConfigurationTest {

    @Test
    void cdInjectsRequiredFcmConfigurationAndCredentialSecret() throws IOException {
        String workflow = Files.readString(Path.of(".github/workflows/cd.yml"));

        assertThat(workflow)
                .contains("FCM_ENABLED=true")
                .contains("FIREBASE_PROJECT_ID=${{ secrets.FIREBASE_PROJECT_ID }}")
                .contains("FIREBASE_SERVICE_ACCOUNT_JSON_BASE64")
                .contains("GOOGLE_APPLICATION_CREDENTIALS=/run/secrets/firebase-service-account.json");
    }

    @Test
    void composeMountsFirebaseCredentialReadOnly() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(compose)
                .contains("./.secrets/firebase-service-account.json:/run/secrets/firebase-service-account.json:ro");
    }
}
