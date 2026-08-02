package com.plog.domain.integration.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProjectIntegrationTest {

    @Test
    void treatsLegacyNullConnectionStatusAsActive() {
        ProjectIntegration integration = ProjectIntegration.builder()
                .providerConnectionId("provider-connection")
                .connectionStatus(null)
                .build();

        assertThat(integration.getConnectionStatus()).isEqualTo(IntegrationConnectionStatus.ACTIVE);
        assertThat(integration.isConnected()).isTrue();
    }

    @Test
    void disconnectClearsOAuthCredentialsButPreservesProviderIdentity() {
        ProjectIntegration integration = ProjectIntegration.builder()
                .externalAccountId("external-account")
                .externalAccountName("Plog workspace")
                .providerConnectionId("provider-connection")
                .accessTokenEncrypted("encrypted-access-token")
                .refreshTokenEncrypted("encrypted-refresh-token")
                .build();

        integration.disconnect();

        assertThat(integration.getConnectionStatus()).isEqualTo(IntegrationConnectionStatus.REVOKED);
        assertThat(integration.isConnected()).isFalse();
        assertThat(integration.getAccessTokenEncrypted()).isNull();
        assertThat(integration.getRefreshTokenEncrypted()).isNull();
        assertThat(integration.getProviderConnectionId()).isEqualTo("provider-connection");
        assertThat(integration.getExternalAccountId()).isEqualTo("external-account");
    }
}
