package com.plog.domain.integration.controller;

import com.plog.domain.integration.controller.docs.IntegrationControllerDoc;
import com.plog.domain.integration.config.IntegrationRedirectProperties;
import com.plog.domain.integration.dto.response.IntegrationAuthorizationResponse;
import com.plog.domain.integration.dto.response.IntegrationConnectionResponse;
import com.plog.domain.integration.dto.response.IntegrationDisconnectionResponse;
import com.plog.domain.integration.dto.response.IntegrationStatusResponse;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.service.FigmaIntegrationService;
import com.plog.domain.integration.service.GithubIntegrationService;
import com.plog.domain.integration.service.GoogleIntegrationService;
import com.plog.domain.integration.service.IntegrationService;
import com.plog.domain.integration.service.NotionIntegrationService;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.IntegrationSuccessCode;
import com.plog.global.api.response.ProjectSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class IntegrationController implements IntegrationControllerDoc {

    private final IntegrationService integrationService;
    private final GithubIntegrationService githubIntegrationService;
    private final FigmaIntegrationService figmaIntegrationService;
    private final NotionIntegrationService notionIntegrationService;
    private final GoogleIntegrationService googleIntegrationService;
    private final IntegrationRedirectProperties redirectProperties;

    @Override
    @GetMapping("/projects/{projectId}/integrations")
    public ResponseEntity<ApiResponse<IntegrationStatusResponse>> getProjectIntegrations(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Long userId
    ) {
        IntegrationStatusResponse response = integrationService.getProjectIntegrations(projectId, userId);

        return ResponseEntity
                .status(ProjectSuccessCode.EXTERNAL_LINKS_RETRIEVED.getHttpStatus())
                .body(ApiResponse.success(ProjectSuccessCode.EXTERNAL_LINKS_RETRIEVED, response));
    }

    @Override
    @PostMapping("/projects/{projectId}/integrations/{provider}/authorization")
    public ResponseEntity<ApiResponse<IntegrationAuthorizationResponse>> issueAuthorizationUrl(
            @PathVariable Long projectId,
            @PathVariable String provider,
            @AuthenticationPrincipal Long userId
    ) {
        IntegrationAuthorizationResponse response = switch (normalize(provider)) {
            case "github" -> githubIntegrationService.issueAuthorizationUrl(projectId, userId);
            case "figma" -> figmaIntegrationService.issueAuthorizationUrl(projectId, userId);
            case "notion" -> notionIntegrationService.issueAuthorizationUrl(projectId, userId);
            case "google" -> googleIntegrationService.issueAuthorizationUrl(projectId, userId);
            default -> throw new ApiException(IntegrationErrorCode.UNSUPPORTED_PROVIDER);
        };
        return ResponseEntity.ok(ApiResponse.success(IntegrationSuccessCode.AUTHORIZATION_URL_ISSUED, response));
    }

    @Override
    @DeleteMapping("/projects/{projectId}/integrations/{provider}")
    public ResponseEntity<ApiResponse<IntegrationDisconnectionResponse>> disconnect(
            @PathVariable Long projectId,
            @PathVariable String provider,
            @AuthenticationPrincipal Long userId
    ) {
        IntegrationDisconnectionResponse response = integrationService.disconnect(
                projectId, userId, parseLinkType(provider));
        return ResponseEntity.ok(ApiResponse.success(IntegrationSuccessCode.INTEGRATION_DISCONNECTED, response));
    }

    @Override
    @GetMapping("/integrations/{provider}/callback")
    public RedirectView integrationCallback(
            @PathVariable String provider,
            @RequestParam String state,
            @RequestParam(required = false, name = "installation_id") String installationId,
            @RequestParam(required = false) String code
    ) {
        String normalizedProvider = normalize(provider);
        try {
            IntegrationConnectionResponse response = switch (normalizedProvider) {
                case "github" -> githubIntegrationService.completeCallback(state, requireCallbackValue(installationId));
                case "figma" -> figmaIntegrationService.completeCallback(state, requireCallbackValue(code));
                case "notion" -> notionIntegrationService.completeCallback(state, requireCallbackValue(code));
                case "google" -> googleIntegrationService.completeCallback(state, requireCallbackValue(code));
                default -> throw new ApiException(IntegrationErrorCode.UNSUPPORTED_PROVIDER);
            };
            return new RedirectView(successRedirectUrl(response));
        } catch (ApiException exception) {
            return new RedirectView(failureRedirectUrl(normalizedProvider, exception));
        }
    }

    private String normalize(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase();
    }

    private LinkType parseLinkType(String provider) {
        return switch (normalize(provider)) {
            case "github" -> LinkType.GITHUB;
            case "figma" -> LinkType.FIGMA;
            case "notion" -> LinkType.NOTION;
            case "google" -> LinkType.GOOGLE;
            default -> throw new ApiException(IntegrationErrorCode.UNSUPPORTED_PROVIDER);
        };
    }

    private String requireCallbackValue(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(IntegrationErrorCode.PROVIDER_AUTHORIZATION_FAILED);
        }
        return value;
    }

    private String successRedirectUrl(IntegrationConnectionResponse response) {
        return UriComponentsBuilder.fromUriString(redirectProperties.successUrl())
                .queryParam("status", "success")
                .queryParam("provider", response.linkType().name().toLowerCase())
                .queryParam("projectId", response.projectId())
                .build()
                .encode()
                .toUriString();
    }

    private String failureRedirectUrl(String provider, ApiException exception) {
        return UriComponentsBuilder.fromUriString(redirectProperties.failureUrl())
                .queryParam("status", "failed")
                .queryParam("provider", provider)
                .queryParam("code", exception.getErrorCode().getCode())
                .build()
                .encode()
                .toUriString();
    }
}
