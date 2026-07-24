package com.plog.domain.integration.service;

import com.plog.domain.integration.dto.response.IntegrationItemResponse;
import com.plog.domain.integration.dto.response.IntegrationDisconnectionResponse;
import com.plog.domain.integration.dto.response.IntegrationStatusResponse;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IntegrationService {

    private static final List<LinkType> SUPPORTED_LINK_TYPES = List.of(
            LinkType.GITHUB,
            LinkType.FIGMA,
            LinkType.NOTION,
            LinkType.GOOGLE
    );

    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final ProjectIntegrationRepository projectIntegrationRepository;

    public IntegrationStatusResponse getProjectIntegrations(Long projectId, Long userId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }

        ProjectMember projectMember = projectAccessService.requireActiveMember(projectId, userId);

        Map<LinkType, ProjectIntegration> connectionsByType = projectIntegrationRepository
                .findAllByProjectIdOrderByLinkTypeAsc(projectId)
                .stream()
                .collect(Collectors.toMap(
                        ProjectIntegration::getLinkType,
                        Function.identity(),
                        (existing, ignored) -> existing
                ));

        List<IntegrationItemResponse> integrations = SUPPORTED_LINK_TYPES.stream()
                .map(linkType -> toResponse(linkType, connectionsByType.get(linkType)))
                .toList();

        return new IntegrationStatusResponse(projectId, projectMember.getId(), integrations);
    }

    @Transactional
    public IntegrationDisconnectionResponse disconnect(Long projectId, Long userId, LinkType linkType) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }

        projectAccessService.requireActiveMember(projectId, userId);

        ProjectIntegration integration = projectIntegrationRepository.findByProjectIdAndLinkType(projectId, linkType)
                .filter(ProjectIntegration::isConnected)
                .orElseThrow(() -> new ApiException(IntegrationErrorCode.PROJECT_INTEGRATION_NOT_FOUND));
        projectIntegrationRepository.delete(integration);
        return new IntegrationDisconnectionResponse(projectId, linkType);
    }

    private IntegrationItemResponse toResponse(LinkType linkType, ProjectIntegration integration) {
        if (integration == null || !integration.isConnected()) {
            return new IntegrationItemResponse(linkType, false, null);
        }

        return new IntegrationItemResponse(
                linkType,
                true,
                integration.getExternalAccountName()
        );
    }
}
