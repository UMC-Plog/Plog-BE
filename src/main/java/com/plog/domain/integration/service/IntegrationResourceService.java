package com.plog.domain.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.plog.domain.integration.dto.request.FigmaResourceRegisterRequest;
import com.plog.domain.integration.dto.request.GoogleResourceRegisterRequest;
import com.plog.domain.integration.dto.request.NotionResourceRegisterRequest;
import com.plog.domain.integration.dto.NotionResourceType;
import com.plog.domain.integration.dto.response.IntegrationResourceCandidateResponse;
import com.plog.domain.integration.dto.response.IntegrationResourceListResponse;
import com.plog.domain.integration.dto.response.IntegrationResourceResponse;
import com.plog.domain.integration.dto.response.IntegrationResourceRemovalResponse;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.IntegrationResourceStatus;
import com.plog.domain.integration.entity.IntegrationResourceType;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.IntegrationActivityRepository;
import com.plog.domain.integration.repository.IntegrationResourceRepository;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** 선택 리소스의 provider 검증·등록만 담당한다. 활동 수집과 기여도 계산은 별도 서비스 책임이다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationResourceService {

    private static final String NOTION_VERSION = "2026-03-11";
    private static final Pattern FIGMA_FILE_KEY = Pattern.compile("/(?:file|design)/([A-Za-z0-9]+)");
    private static final String GOOGLE_DOCUMENT_MIME = "application/vnd.google-apps.document";
    private static final String GOOGLE_PRESENTATION_MIME = "application/vnd.google-apps.presentation";

    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final ProjectIntegrationRepository projectIntegrationRepository;
    private final ProjectIntegrationService projectIntegrationService;
    private final IntegrationVerificationService integrationVerificationService;
    private final IntegrationResourceRepository integrationResourceRepository;
    private final IntegrationActivityRepository integrationActivityRepository;
    private final GithubAppClient githubAppClient;
    private final RestClient restClient = ProviderRestClientFactory.create();

    @Transactional(readOnly = true)
    public IntegrationResourceListResponse getResources(Long projectId, Long userId, LinkType linkType) {
        requireActiveMember(projectId, userId);
        ProjectIntegration integration = requireConnectedIntegration(projectId, linkType);
        List<IntegrationResourceResponse> resources = integrationResourceRepository
                .findAllByProjectIntegrationIdOrderByIdAsc(integration.getId())
                .stream()
                .map(this::toResponse)
                .toList();
        return new IntegrationResourceListResponse(projectId, linkType, resources);
    }

    @Transactional(readOnly = true)
    public List<IntegrationResourceCandidateResponse> getNotionCandidates(
            Long projectId, Long userId, String query
    ) {
        requireActiveMember(projectId, userId);
        ProjectIntegration integration = integrationVerificationService.requireVerifiedConnection(projectId, LinkType.NOTION);
        return searchNotionResources(integration, query);
    }

    @Transactional
    public IntegrationResourceResponse registerNotion(
            Long projectId, Long userId, NotionResourceRegisterRequest request
    ) {
        ProjectMember member = requireActiveMember(projectId, userId);
        ProjectIntegration integration = integrationVerificationService.requireVerifiedConnection(projectId, LinkType.NOTION);
        ValidatedResource resource = validateNotionResource(integration, request);
        return toResponse(saveResource(integration, member, resource));
    }

    @Transactional
    public IntegrationResourceResponse registerGoogle(
            Long projectId, Long userId, LinkType linkType, GoogleResourceRegisterRequest request
    ) {
        ProjectMember member = requireActiveMember(projectId, userId);
        ProjectIntegration integration = integrationVerificationService.requireVerifiedConnection(projectId, linkType);
        ValidatedResource resource = validateGoogleResource(integration, request);
        validateResourceMatchesLinkType(linkType, resource.resourceType());
        return toResponse(saveResource(integration, member, resource));
    }

    private void validateResourceMatchesLinkType(LinkType linkType, IntegrationResourceType resourceType) {
        boolean matches = (linkType == LinkType.GOOGLE_DOCS && resourceType == IntegrationResourceType.GOOGLE_DOCUMENT)
                || (linkType == LinkType.GOOGLE_SLIDES && resourceType == IntegrationResourceType.GOOGLE_PRESENTATION);
        if (!matches) {
            throw new ApiException(IntegrationErrorCode.UNSUPPORTED_GOOGLE_RESOURCE_TYPE);
        }
    }
    @Transactional
    public IntegrationResourceResponse registerFigma(
            Long projectId, Long userId, FigmaResourceRegisterRequest request
    ) {
        ProjectMember member = requireActiveMember(projectId, userId);
        ProjectIntegration integration = integrationVerificationService.requireVerifiedConnection(projectId, LinkType.FIGMA);
        ValidatedResource resource = validateFigmaResource(integration, request);
        return toResponse(saveResource(integration, member, resource));
    }

    @Transactional
    public IntegrationResourceRemovalResponse removeResource(
            Long projectId, Long userId, LinkType linkType, Long resourceId
    ) {
        requireActiveMember(projectId, userId);
        if (linkType == LinkType.GITHUB) {
            throw new ApiException(IntegrationErrorCode.GITHUB_RESOURCE_MANAGED_BY_PROVIDER);
        }
        ProjectIntegration integration = requireConnectedIntegration(projectId, linkType);
        IntegrationResource resource = integrationResourceRepository
                .findByIdAndProjectIntegrationIdForUpdate(resourceId, integration.getId())
                .orElseThrow(() -> new ApiException(IntegrationErrorCode.EXTERNAL_RESOURCE_NOT_FOUND));

        integrationActivityRepository.deleteAllByIntegrationResourceId(resource.getId());
        integrationResourceRepository.delete(resource);
        return new IntegrationResourceRemovalResponse(projectId, linkType, resource.getId());
    }

    @Transactional
    public void registerGithubInstallationRepositories(ProjectIntegration integration) {
        if (!integration.isConnected() || integration.getLinkType() != LinkType.GITHUB) {
            return;
        }
        ProjectMember connectedBy = integration.getConnectedByProjectMember();
        GithubAppClient.RepositoryListing repositoryListing = githubAppClient.listInstallationRepositories(
                integration.getProviderConnectionId());
        List<GithubAppClient.Repository> repositories = repositoryListing.repositories();
        java.util.Set<String> selectedRepositoryIds = repositories.stream()
                .map(GithubAppClient.Repository::id)
                .collect(Collectors.toSet());
        List<IntegrationResource> existingResources = integrationResourceRepository
                .findAllByProjectIntegrationIdOrderByIdAsc(integration.getId());
        if (repositoryListing.complete()) {
            Instant now = Instant.now();
            existingResources.stream()
                    .filter(resource -> resource.getResourceType() == IntegrationResourceType.GITHUB_REPOSITORY)
                    .filter(resource -> !selectedRepositoryIds.contains(resource.getProviderResourceId()))
                    .forEach(resource -> resource.disable(now));
        }
        Map<String, IntegrationResource> existingResourceByProviderId = existingResources.stream()
                .collect(Collectors.toMap(
                        IntegrationResource::getProviderResourceId,
                        Function.identity(),
                        (existing, ignored) -> existing
                ));
        for (GithubAppClient.Repository repository : repositories) {
            IntegrationResource existing = existingResourceByProviderId.get(repository.id());
            if (existing != null) {
                existing.updateProviderMetadata(
                        connectedBy, repository.fullName(), repository.htmlUrl(), repository.payload(),
                        repository.lastModifiedAt());
                continue;
            }
            saveResource(integration, connectedBy, new ValidatedResource(
                    IntegrationResourceType.GITHUB_REPOSITORY,
                    repository.id(),
                    repository.fullName(),
                    repository.htmlUrl(),
                    repository.payload(),
                    repository.lastModifiedAt()
            ));
        }
    }

    @Transactional
    public void registerGithubInstallationRepositories(Long projectId) {
        projectIntegrationRepository.findByProjectIdAndLinkType(projectId, LinkType.GITHUB)
                .filter(ProjectIntegration::isConnected)
                .ifPresent(this::registerGithubInstallationRepositories);
    }

    private ProjectMember requireActiveMember(Long projectId, Long userId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
        return projectAccessService.requireActiveMember(projectId, userId);
    }

    private ProjectIntegration requireConnectedIntegration(Long projectId, LinkType linkType) {
        return projectIntegrationRepository.findByProjectIdAndLinkType(projectId, linkType)
                .filter(ProjectIntegration::isConnected)
                .orElseThrow(() -> new ApiException(IntegrationErrorCode.PROJECT_INTEGRATION_NOT_FOUND));
    }

    private List<IntegrationResourceCandidateResponse> searchNotionResources(
            ProjectIntegration integration,
            String query
    ) {
        try {
            JsonNode body = restClient.post()
                    .uri("https://api.notion.com/v1/search")
                    .header("Notion-Version", NOTION_VERSION)
                    .headers(headers -> headers.setBearerAuth(projectIntegrationService.decryptAccessToken(integration)))
                    .body(Map.of("query", query == null ? "" : query.trim(), "page_size", 100))
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || !body.path("results").isArray()) {
                return List.of();
            }
            List<IntegrationResourceCandidateResponse> candidates = new ArrayList<>();
            for (JsonNode candidate : body.path("results")) {
                NotionResourceType type = notionCandidateResourceType(candidate.path("object").asText());
                if (type == null) {
                    continue;
                }
                candidates.add(new IntegrationResourceCandidateResponse(
                        candidate.path("id").asText(), type, notionTitle(candidate),
                        candidate.path("url").asText(null), parseInstant(candidate.path("last_edited_time").asText(null))
                ));
            }
            return candidates;
        } catch (RestClientResponseException exception) {
            throw providerException("Notion 리소스 검색", exception);
        } catch (RestClientException exception) {
            throw providerUnavailableException("Notion 리소스 검색", exception);
        }
    }

    private ValidatedResource validateNotionResource(
            ProjectIntegration integration,
            NotionResourceRegisterRequest request
    ) {
        if (request == null) {
            throw new ApiException(IntegrationErrorCode.EXTERNAL_RESOURCE_NOT_FOUND);
        }
        IntegrationResourceType resourceType = request.resourceType() == NotionResourceType.PAGE
                ? IntegrationResourceType.NOTION_PAGE
                : IntegrationResourceType.NOTION_DATA_SOURCE;
        String uriTemplate = resourceType == IntegrationResourceType.NOTION_PAGE
                ? "https://api.notion.com/v1/pages/{resourceId}"
                : "https://api.notion.com/v1/data_sources/{resourceId}";
        try {
            JsonNode body = restClient.get()
                    .uri(uriTemplate, request.providerResourceId())
                    .header("Notion-Version", NOTION_VERSION)
                    .headers(headers -> headers.setBearerAuth(projectIntegrationService.decryptAccessToken(integration)))
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || body.path("id").asText().isBlank()) {
                throw new ApiException(IntegrationErrorCode.EXTERNAL_RESOURCE_NOT_FOUND);
            }
            return new ValidatedResource(
                    resourceType, body.path("id").asText(), notionTitle(body),
                    body.path("url").asText(null), body.toString(),
                    parseInstant(body.path("last_edited_time").asText(null))
            );
        } catch (RestClientResponseException exception) {
            throw providerException("Notion 리소스 검증", exception);
        } catch (RestClientException exception) {
            throw providerUnavailableException("Notion 리소스 검증", exception);
        }
    }

    private ValidatedResource validateGoogleResource(
            ProjectIntegration integration,
            GoogleResourceRegisterRequest request
    ) {
        if (request == null || request.fileId() == null || request.fileId().isBlank()) {
            throw new ApiException(IntegrationErrorCode.EXTERNAL_RESOURCE_NOT_FOUND);
        }
        try {
            JsonNode body = restClient.get()
                    .uri("https://www.googleapis.com/drive/v3/files/{fileId}?fields=id,name,mimeType,webViewLink,createdTime,modifiedTime,lastModifyingUser,owners",
                            request.fileId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + projectIntegrationService.decryptAccessToken(integration))
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || body.path("id").asText().isBlank()) {
                throw new ApiException(IntegrationErrorCode.EXTERNAL_RESOURCE_NOT_FOUND);
            }
            IntegrationResourceType resourceType = googleResourceType(body);
            if (resourceType == null) {
                throw new ApiException(IntegrationErrorCode.UNSUPPORTED_GOOGLE_RESOURCE_TYPE);
            }
            return new ValidatedResource(
                    resourceType, body.path("id").asText(), body.path("name").asText(body.path("id").asText()),
                    body.path("webViewLink").asText(null), body.toString(),
                    parseInstant(body.path("modifiedTime").asText(null))
            );
        } catch (RestClientResponseException exception) {
            throw providerException("Google Drive 리소스 검증", exception);
        } catch (RestClientException exception) {
            throw providerUnavailableException("Google Drive 리소스 검증", exception);
        }
    }

    private ValidatedResource validateFigmaResource(
            ProjectIntegration integration,
            FigmaResourceRegisterRequest request
    ) {
        if (request == null || request.fileUrl() == null || request.fileUrl().isBlank()) {
            throw new ApiException(IntegrationErrorCode.EXTERNAL_RESOURCE_NOT_FOUND);
        }
        String fileKey = parseFigmaFileKey(request.fileUrl());
        try {
            JsonNode body = restClient.get()
                    .uri("https://api.figma.com/v1/files/{fileKey}?depth=1", fileKey)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + projectIntegrationService.decryptAccessToken(integration))
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || body.path("name").asText().isBlank()) {
                throw new ApiException(IntegrationErrorCode.EXTERNAL_RESOURCE_NOT_FOUND);
            }
            return new ValidatedResource(
                    IntegrationResourceType.FIGMA_FILE, fileKey, body.path("name").asText(),
                    request.fileUrl(), body.toString(), parseInstant(body.path("lastModified").asText(null))
            );
        } catch (RestClientResponseException exception) {
            throw providerException("Figma 리소스 검증", exception);
        } catch (RestClientException exception) {
            throw providerUnavailableException("Figma 리소스 검증", exception);
        }
    }

    private IntegrationResource saveResource(
            ProjectIntegration integration,
            ProjectMember member,
            ValidatedResource resource
    ) {
        IntegrationResource existing = integrationResourceRepository.findByProjectIntegrationIdAndProviderResourceId(
                integration.getId(), resource.providerResourceId()).orElse(null);
        if (existing != null) {
            if (existing.getResourceStatus() != IntegrationResourceStatus.ACTIVE) {
                existing.reactivate(
                        member, resource.resourceName(), resource.resourceUrl(), resource.providerMetadata(),
                        resource.lastModifiedAt());
                return existing;
            }
            throw new ApiException(IntegrationErrorCode.EXTERNAL_RESOURCE_ALREADY_REGISTERED);
        }
        try {
            return integrationResourceRepository.saveAndFlush(IntegrationResource.builder()
                    .projectIntegration(integration)
                    .selectedByProjectMember(member)
                    .resourceType(resource.resourceType())
                    .providerResourceId(resource.providerResourceId())
                    .resourceName(resource.resourceName())
                    .resourceUrl(resource.resourceUrl())
                    .providerMetadata(resource.providerMetadata())
                    .resourceStatus(IntegrationResourceStatus.ACTIVE)
                    .lastModifiedAt(resource.lastModifiedAt())
                    .build());
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(IntegrationErrorCode.EXTERNAL_RESOURCE_ALREADY_REGISTERED, exception);
        }
    }

    private NotionResourceType notionCandidateResourceType(String object) {
        return switch (object) {
            case "page" -> NotionResourceType.PAGE;
            case "data_source" -> NotionResourceType.DATA_SOURCE;
            default -> null;
        };
    }

    private String notionTitle(JsonNode node) {
        JsonNode title = node.path("title");
        if (title.isArray() && !title.isEmpty()) {
            return title.get(0).path("plain_text").asText(node.path("id").asText());
        }
        JsonNode properties = node.path("properties");
        if (properties.isObject()) {
            for (JsonNode property : properties) {
                JsonNode propertyTitle = property.path("title");
                if (propertyTitle.isArray() && !propertyTitle.isEmpty()) {
                    return propertyTitle.get(0).path("plain_text").asText(node.path("id").asText());
                }
            }
        }
        return node.path("id").asText();
    }

    private IntegrationResourceType googleResourceType(JsonNode file) {
        String mimeType = file.path("mimeType").asText();
        if (GOOGLE_DOCUMENT_MIME.equals(mimeType)) {
            return IntegrationResourceType.GOOGLE_DOCUMENT;
        }
        if (GOOGLE_PRESENTATION_MIME.equals(mimeType)) {
            return IntegrationResourceType.GOOGLE_PRESENTATION;
        }
        return null;
    }

    private String parseFigmaFileKey(String resourceUrl) {
        Matcher matcher = FIGMA_FILE_KEY.matcher(resourceUrl);
        if (!matcher.find()) {
            throw new ApiException(IntegrationErrorCode.INVALID_EXTERNAL_RESOURCE_URL);
        }
        return matcher.group(1);
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private ApiException providerException(String context, RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        log.warn("{} 중 provider 응답 에러. status={}, body={}", context, status,
                ProviderResponseLogSupport.sanitizeForLog(exception.getResponseBodyAsString()));
        if (status == 401 || status == 403) {
            return new ApiException(IntegrationErrorCode.PROVIDER_RESOURCE_ACCESS_DENIED, exception);
        }
        if (status == 404) {
            return new ApiException(IntegrationErrorCode.EXTERNAL_RESOURCE_NOT_FOUND, exception);
        }
        return new ApiException(IntegrationErrorCode.PROVIDER_TEMPORARILY_UNAVAILABLE, exception);
    }

    private ApiException providerUnavailableException(String context, RestClientException exception) {
        log.warn("{} 중 provider 응답 없음 (timeout/connection issue).", context, exception);
        return new ApiException(IntegrationErrorCode.PROVIDER_TEMPORARILY_UNAVAILABLE, exception);
    }

    private IntegrationResourceResponse toResponse(IntegrationResource resource) {
        return new IntegrationResourceResponse(
                resource.getId(), resource.getProviderResourceId(), resource.getResourceType(), resource.getResourceName(),
                resource.getResourceUrl(), resource.getResourceStatus(), resource.getLastModifiedAt(), resource.getLastCollectedAt()
        );
    }

    private record ValidatedResource(
            IntegrationResourceType resourceType,
            String providerResourceId,
            String resourceName,
            String resourceUrl,
            String providerMetadata,
            Instant lastModifiedAt
    ) {
    }
}