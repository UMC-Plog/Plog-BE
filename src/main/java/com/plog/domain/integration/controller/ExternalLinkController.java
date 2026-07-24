package com.plog.domain.integration.controller;

import com.plog.domain.integration.config.IntegrationRedirectProperties;
import com.plog.domain.integration.dto.response.ExternalLinkStatusResponse;
import com.plog.domain.integration.dto.response.IntegrationAuthorizationResponse;
import com.plog.domain.integration.dto.response.IntegrationConnectionResponse;
import com.plog.domain.integration.dto.response.IntegrationDisconnectionResponse;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.service.ExternalLinkService;
import com.plog.domain.integration.service.FigmaIntegrationService;
import com.plog.domain.integration.service.GithubIntegrationService;
import com.plog.domain.integration.service.GoogleIntegrationService;
import com.plog.domain.integration.service.NotionIntegrationService;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.IntegrationSuccessCode;
import com.plog.global.api.response.ProjectSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "external-link", description = "외부 계정 연동 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ExternalLinkController {

    private final ExternalLinkService externalLinkService;
    private final GithubIntegrationService githubIntegrationService;
    private final FigmaIntegrationService figmaIntegrationService;
    private final NotionIntegrationService notionIntegrationService;
    private final GoogleIntegrationService googleIntegrationService;
    private final IntegrationRedirectProperties redirectProperties;

    @Operation(
            summary = "내 외부 툴 연동 상태 조회",
            description = "현재 사용자의 프로젝트 외부 툴 연동 상태를 조회합니다."
    )
    @GetMapping("/projects/{projectId}/me/external-links")
    public ResponseEntity<ApiResponse<ExternalLinkStatusResponse>> getMyExternalLinks(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Long userId
    ) {
        ExternalLinkStatusResponse response = externalLinkService.getMyExternalLinks(projectId, userId);

        return ResponseEntity
                .status(ProjectSuccessCode.EXTERNAL_LINKS_RETRIEVED.getHttpStatus())
                .body(ApiResponse.success(ProjectSuccessCode.EXTERNAL_LINKS_RETRIEVED, response));
    }

    @Operation(
            summary = "외부 계정 연동 URL 발급",
            description = "프로젝트 멤버가 provider 승인 화면으로 이동할 URL을 발급합니다. provider는 github, figma, notion, google을 지원합니다."
    )
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

    @Operation(
            summary = "외부 계정 연동 해제",
            description = "프로젝트에 저장된 provider 연동 정보를 삭제합니다. provider는 github, figma, notion, google을 지원합니다."
    )
    @DeleteMapping("/projects/{projectId}/integrations/{provider}")
    public ResponseEntity<ApiResponse<IntegrationDisconnectionResponse>> disconnect(
            @PathVariable Long projectId,
            @PathVariable String provider,
            @AuthenticationPrincipal Long userId
    ) {
        IntegrationDisconnectionResponse response = externalLinkService.disconnect(
                projectId, userId, parseLinkType(provider));
        return ResponseEntity.ok(ApiResponse.success(IntegrationSuccessCode.INTEGRATION_DISCONNECTED, response));
    }

    @Operation(
            summary = "외부 계정 연동 callback",
            description = "provider가 승인 완료 후 호출하는 callback입니다. GitHub는 installation_id, OAuth provider는 code를 전달합니다."
    )
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
