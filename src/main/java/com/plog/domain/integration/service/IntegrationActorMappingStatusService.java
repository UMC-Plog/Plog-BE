package com.plog.domain.integration.service;

import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.integration.repository.ProjectMemberIntegrationIdentityRepository;
import com.plog.domain.project.entity.MemberStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IntegrationActorMappingStatusService {

    private final ProjectIntegrationRepository projectIntegrationRepository;
    private final ProjectMemberIntegrationIdentityRepository identityRepository;

    public boolean isMyMappingCompleted(Long projectId, Long projectMemberId) {
        return connectedIntegrations(projectId).stream()
                .allMatch(integration -> identityRepository
                        .findByProjectIntegrationIdAndProjectMemberId(integration.getId(), projectMemberId)
                        .isPresent());
    }

    public boolean areAllActiveMemberMappingsCompleted(Long projectId, long activeMemberCount) {
        return connectedIntegrations(projectId).stream()
                .allMatch(integration -> identityRepository
                        .countByProjectIntegrationIdAndProjectMemberStatus(
                                integration.getId(), MemberStatus.ACTIVE) >= activeMemberCount);
    }

    private List<ProjectIntegration> connectedIntegrations(Long projectId) {
        return projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(projectId).stream()
                .filter(ProjectIntegration::isConnected)
                .toList();
    }
}
