package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.plog.domain.integration.config.FigmaIntegrationProperties;
import com.plog.domain.integration.dto.response.IntegrationAuthorizationResponse;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectAccessService;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FigmaIntegrationServiceAuthorizationTest {

    @Test
    @DisplayName("Figma 승인 URL은 파일 원문 권한 없이 메타데이터와 활동 권한만 요청한다")
    void issuesAuthorizationUrlWithoutFileContentScope() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectAccessService projectAccessService = mock(ProjectAccessService.class);
        IntegrationAuthorizationStateService authorizationStateService =
                mock(IntegrationAuthorizationStateService.class);
        ProjectIntegrationService projectIntegrationService = mock(ProjectIntegrationService.class);
        ProjectMember member = mock(ProjectMember.class);
        Instant expiresAt = Instant.parse("2026-08-12T03:30:00Z");
        given(projectRepository.existsById(10L)).willReturn(true);
        given(projectAccessService.requireActiveMember(10L, 20L)).willReturn(member);
        given(authorizationStateService.issue(member, LinkType.FIGMA))
                .willReturn(new IntegrationAuthorizationStateService.IssuedState("state-value", expiresAt));
        FigmaIntegrationService service = new FigmaIntegrationService(
                new FigmaIntegrationProperties(
                        "client-id", "client-secret", "https://api.plog.com/api/integrations/figma/callback"),
                projectRepository,
                projectAccessService,
                authorizationStateService,
                projectIntegrationService
        );

        IntegrationAuthorizationResponse response = service.issueAuthorizationUrl(10L, 20L);

        String authorizationUrl = URLDecoder.decode(response.authorizationUrl(), StandardCharsets.UTF_8);
        assertThat(authorizationUrl)
                .contains("file_metadata:read")
                .contains("file_versions:read")
                .contains("file_comments:read")
                .doesNotContain("file_content:read");
    }
}
