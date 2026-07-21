package com.plog.domain.integration.service;

import com.plog.domain.integration.dto.response.ExternalLinkItemResponse;
import com.plog.domain.integration.dto.response.ExternalLinkStatusResponse;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.repository.ExternalConnectionRepository;
import com.plog.domain.integration.repository.ExternalConnectionSummary;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectAccessService;
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
public class ExternalLinkService {

    private static final List<LinkType> SUPPORTED_LINK_TYPES = List.of(
            LinkType.GITHUB,
            LinkType.FIGMA,
            LinkType.NOTION
    );

    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final ExternalConnectionRepository externalConnectionRepository;

    public ExternalLinkStatusResponse getMyExternalLinks(Long projectId, Long userId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }

        ProjectMember projectMember = projectAccessService.requireActiveMember(projectId, userId);

        Map<LinkType, ExternalConnectionSummary> connectionsByType = externalConnectionRepository
                .findAllByProjectMemberId(projectMember.getId())
                .stream()
                .collect(Collectors.toMap(
                        ExternalConnectionSummary::getLinkType,
                        Function.identity(),
                        (existing, ignored) -> existing
                ));

        List<ExternalLinkItemResponse> links = SUPPORTED_LINK_TYPES.stream()
                .map(linkType -> toResponse(linkType, connectionsByType.get(linkType)))
                .toList();

        return new ExternalLinkStatusResponse(projectId, projectMember.getId(), links);
    }

    private ExternalLinkItemResponse toResponse(LinkType linkType, ExternalConnectionSummary connection) {
        if (connection == null || connection.getExternalAccountId() == null) {
            return new ExternalLinkItemResponse(linkType, false, null);
        }

        return new ExternalLinkItemResponse(
                linkType,
                true,
                connection.getExternalAccountId()
        );
    }
}
