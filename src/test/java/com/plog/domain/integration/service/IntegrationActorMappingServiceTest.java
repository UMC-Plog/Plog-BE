package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.ProjectMemberIntegrationIdentityAliasRepository;
import com.plog.domain.integration.repository.ProjectMemberIntegrationIdentityRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntegrationActorMappingServiceTest {

    @Mock
    private ProjectMemberIntegrationIdentityRepository identityRepository;

    @Mock
    private ProjectMemberIntegrationIdentityAliasRepository aliasRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @InjectMocks
    private IntegrationActorMappingService integrationActorMappingService;

    @Test
    void queriesOnlyTheMatchingActiveProjectMemberForEmailFallback() {
        ProjectMember member = ProjectMember.builder().id(10L).build();
        Project project = mock(Project.class);
        given(project.getId()).willReturn(1L);
        ProjectIntegration integration = ProjectIntegration.builder()
                .id(2L)
                .project(project)
                .build();
        given(identityRepository.findByProjectIntegrationIdAndProviderActorId(2L, "actor-1"))
                .willReturn(Optional.empty());
        given(aliasRepository.findAllByProjectIntegrationIdAndAliasTypeAndAliasValue(any(), any(), any()))
                .willReturn(List.of());
        given(projectMemberRepository.findAllByProjectIdAndStatusAndUserEmailIgnoreCase(
                1L, MemberStatus.ACTIVE, "vana@plog.test"
        )).willReturn(List.of(member));

        ProjectMember resolved = integrationActorMappingService.resolve(
                integration, "actor-1", "vana", "vana@plog.test"
        );

        assertThat(resolved).isSameAs(member);
        verify(projectMemberRepository).findAllByProjectIdAndStatusAndUserEmailIgnoreCase(
                1L, MemberStatus.ACTIVE, "vana@plog.test"
        );
        verify(projectMemberRepository, never()).findActiveMembers(any(), eq(MemberStatus.ACTIVE));
    }
}
