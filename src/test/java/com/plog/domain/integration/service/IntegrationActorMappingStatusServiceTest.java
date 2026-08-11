package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.integration.repository.ProjectMemberIntegrationIdentityRepository;
import com.plog.domain.project.entity.MemberStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntegrationActorMappingStatusServiceTest {

    @Mock
    private ProjectIntegrationRepository projectIntegrationRepository;

    @Mock
    private ProjectMemberIntegrationIdentityRepository identityRepository;

    @InjectMocks
    private IntegrationActorMappingStatusService statusService;

    @Test
    void completesMyMappingWhenEveryConnectedIntegrationHasMyIdentity() {
        ProjectIntegration connected = integration(10L, true);
        ProjectIntegration disconnected = integration(11L, false);
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(1L))
                .thenReturn(List.of(connected, disconnected));
        when(identityRepository.findByProjectIntegrationIdAndProjectMemberId(10L, 20L))
                .thenReturn(Optional.of(mock(com.plog.domain.integration.entity.ProjectMemberIntegrationIdentity.class)));

        boolean completed = statusService.isMyMappingCompleted(1L, 20L);

        assertThat(completed).isTrue();
    }

    @Test
    void keepsMyMappingIncompleteWhenAConnectedIntegrationHasNoIdentity() {
        ProjectIntegration first = integration(10L, true);
        ProjectIntegration second = integration(11L, true);
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(1L))
                .thenReturn(List.of(first, second));
        when(identityRepository.findByProjectIntegrationIdAndProjectMemberId(10L, 20L))
                .thenReturn(Optional.of(mock(com.plog.domain.integration.entity.ProjectMemberIntegrationIdentity.class)));
        when(identityRepository.findByProjectIntegrationIdAndProjectMemberId(11L, 20L))
                .thenReturn(Optional.empty());

        assertThat(statusService.isMyMappingCompleted(1L, 20L)).isFalse();
    }

    @Test
    void requiresEveryActiveMemberMappingForEveryConnectedIntegration() {
        ProjectIntegration first = integration(10L, true);
        ProjectIntegration second = integration(11L, true);
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(1L))
                .thenReturn(List.of(first, second));
        when(identityRepository.countByProjectIntegrationIdAndProjectMemberStatus(10L, MemberStatus.ACTIVE))
                .thenReturn(3L);
        when(identityRepository.countByProjectIntegrationIdAndProjectMemberStatus(11L, MemberStatus.ACTIVE))
                .thenReturn(2L);

        assertThat(statusService.areAllActiveMemberMappingsCompleted(1L, 3L)).isFalse();
    }

    private ProjectIntegration integration(Long id, boolean connected) {
        ProjectIntegration integration = mock(ProjectIntegration.class);
        when(integration.isConnected()).thenReturn(connected);
        if (connected) {
            when(integration.getId()).thenReturn(id);
        }
        return integration;
    }
}
