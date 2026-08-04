package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.exception.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntegrationVerificationServiceTest {

    @Mock
    private GithubAppClient githubAppClient;
    @Mock
    private FigmaIntegrationService figmaIntegrationService;
    @Mock
    private NotionIntegrationService notionIntegrationService;
    @Mock
    private GoogleIntegrationService googleIntegrationService;
    @Mock
    private ProjectIntegrationRepository projectIntegrationRepository;
    @Mock
    private ProjectIntegrationService projectIntegrationService;

    @InjectMocks
    private IntegrationVerificationService service;

    @Test
    void preservesIntegrationHistoryWhenProviderAuthorizationWasRevoked() {
        ProjectIntegration integration = ProjectIntegration.builder()
                .id(10L)
                .linkType(LinkType.GITHUB)
                .providerConnectionId("installation-1")
                .build();
        given(projectIntegrationRepository.findByProjectIdAndLinkType(1L, LinkType.GITHUB))
                .willReturn(Optional.of(integration));
        given(githubAppClient.verifyInstallation("installation-1"))
                .willReturn(IntegrationVerificationStatus.DISCONNECTED);

        assertThatThrownBy(() -> service.requireVerifiedConnection(1L, LinkType.GITHUB))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(IntegrationErrorCode.PROVIDER_REAUTHORIZATION_REQUIRED));

        verify(projectIntegrationService).requireReauthorization(10L);
    }
}
