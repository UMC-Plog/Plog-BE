package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.plog.domain.integration.entity.IntegrationIdentityAliasType;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.entity.ProjectMemberIntegrationIdentity;
import com.plog.domain.integration.entity.ProjectMemberIntegrationIdentityAlias;
import com.plog.domain.integration.repository.ProjectMemberIntegrationIdentityAliasRepository;
import com.plog.domain.integration.repository.ProjectMemberIntegrationIdentityRepository;
import com.plog.domain.project.entity.ProjectMember;
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

    @InjectMocks
    private IntegrationActorMappingService integrationActorMappingService;

    @Test
    void treatsIdPrefixedStoredValueAsRawProviderId() {
        assertThat(ProviderActorKey.fromStored("id:provider-user"))
                .isEqualTo(ProviderActorKey.providerId("id:provider-user"));
    }

    @Test
    void resolvesAnExplicitProviderActorIdMappingFirst() {
        ProjectMember member = ProjectMember.builder().id(10L).build();
        ProjectIntegration integration = ProjectIntegration.builder()
                .id(2L)
                .linkType(LinkType.GITHUB)
                .build();
        given(identityRepository.findByProjectIntegrationIdAndProviderActorId(2L, "actor-1"))
                .willReturn(Optional.of(ProjectMemberIntegrationIdentity.builder()
                        .projectMember(member)
                        .build()));

        ProjectMember resolved = integrationActorMappingService.resolve(
                integration, "actor-1", "vana", "vana@plog.test"
        );

        assertThat(resolved).isSameAs(member);
    }

    @Test
    void resolvesOnlyExplicitAliasesAndDoesNotTrustAnUnregisteredActivityEmail() {
        ProjectMember member = ProjectMember.builder().id(10L).build();
        ProjectIntegration integration = ProjectIntegration.builder()
                .id(2L)
                .linkType(LinkType.GITHUB)
                .build();
        ProjectMemberIntegrationIdentity identity = ProjectMemberIntegrationIdentity.builder()
                .projectMember(member)
                .build();
        given(identityRepository.findByProjectIntegrationIdAndProviderActorId(
                2L, "email:vana@plog.test"
        ))
                .willReturn(Optional.empty());
        given(identityRepository.findByProjectIntegrationIdAndProviderActorId(2L, "login:vana"))
                .willReturn(Optional.empty());
        given(aliasRepository.findAllByProjectIntegrationIdAndAliasTypeAndAliasValue(
                2L, IntegrationIdentityAliasType.EMAIL, "vana@plog.test"
        )).willReturn(List.of());
        given(aliasRepository.findAllByProjectIntegrationIdAndAliasTypeAndAliasValue(
                2L, IntegrationIdentityAliasType.LOGIN, "vana"
        )).willReturn(List.of(ProjectMemberIntegrationIdentityAlias.builder()
                .identity(identity)
                .build()));

        ProjectMember resolved = integrationActorMappingService.resolve(
                integration, null, "vana", "vana@plog.test"
        );
        ProjectMember unresolved = integrationActorMappingService.resolve(
                integration, null, null, "unregistered@plog.test"
        );

        assertThat(resolved).isSameAs(member);
        assertThat(unresolved).isNull();
    }

    @Test
    void doesNotFallbackToSharedEmailWhenAStableProviderIdIsPresent() {
        ProjectIntegration integration = ProjectIntegration.builder()
                .id(2L)
                .linkType(LinkType.GITHUB)
                .build();
        given(identityRepository.findByProjectIntegrationIdAndProviderActorId(2L, "actor-2"))
                .willReturn(Optional.empty());

        ProjectMember resolved = integrationActorMappingService.resolve(
                integration, "actor-2", "shared", "shared@plog.test"
        );

        assertThat(resolved).isNull();
    }

    @Test
    void resolvesASelectedLoginOnlyActorWithoutRequiringAnAlias() {
        ProjectMember member = ProjectMember.builder().id(10L).build();
        ProjectIntegration integration = ProjectIntegration.builder()
                .id(2L)
                .linkType(LinkType.GITHUB)
                .build();
        given(identityRepository.findByProjectIntegrationIdAndProviderActorId(2L, "login:display-name"))
                .willReturn(Optional.of(ProjectMemberIntegrationIdentity.builder()
                        .projectMember(member)
                        .build()));

        ProjectMember resolved = integrationActorMappingService.resolve(
                integration, null, "display-name", null
        );

        assertThat(resolved).isSameAs(member);
    }

    @Test
    void doesNotResolveGoogleNameOnlyActorByLogin() {
        ProjectIntegration integration = ProjectIntegration.builder()
                .id(2L)
                .linkType(LinkType.GOOGLE_DOCS)
                .build();

        ProjectMember resolved = integrationActorMappingService.resolve(
                integration, null, "유상완", null
        );

        assertThat(resolved).isNull();
    }

    @Test
    void resolvesGoogleProviderActorByStrongEmailAliasWhenEndpointProviderIdDiffers() {
        ProjectMember member = ProjectMember.builder().id(10L).build();
        ProjectIntegration integration = ProjectIntegration.builder()
                .id(2L)
                .linkType(LinkType.GOOGLE_DOCS)
                .build();
        ProjectMemberIntegrationIdentity identity = ProjectMemberIntegrationIdentity.builder()
                .projectMember(member)
                .build();
        given(identityRepository.findByProjectIntegrationIdAndProviderActorId(2L, "permission-1"))
                .willReturn(Optional.empty());
        given(aliasRepository.findAllByProjectIntegrationIdAndAliasTypeAndAliasValue(
                2L,
                IntegrationIdentityAliasType.LOGIN,
                ProviderActorKey.googleProviderIdAlias("permission-1")
        )).willReturn(List.of());
        given(identityRepository.findByProjectIntegrationIdAndProviderActorId(2L, "email:self@example.com"))
                .willReturn(Optional.empty());
        given(aliasRepository.findAllByProjectIntegrationIdAndAliasTypeAndAliasValue(
                2L, IntegrationIdentityAliasType.EMAIL, "self@example.com"
        )).willReturn(List.of(ProjectMemberIntegrationIdentityAlias.builder()
                .identity(identity)
                .build()));

        ProjectMember resolved = integrationActorMappingService.resolve(
                integration, "permission-1", "유상완", "self@example.com"
        );

        assertThat(resolved).isSameAs(member);
    }

    @Test
    void resolvesGoogleActorByPreviouslyClusteredProviderIdAliasWithoutEmail() {
        ProjectMember member = ProjectMember.builder().id(10L).build();
        ProjectIntegration integration = ProjectIntegration.builder()
                .id(2L)
                .linkType(LinkType.GOOGLE_DOCS)
                .build();
        ProjectMemberIntegrationIdentity identity = ProjectMemberIntegrationIdentity.builder()
                .projectMember(member)
                .build();
        given(identityRepository.findByProjectIntegrationIdAndProviderActorId(2L, "permission-1"))
                .willReturn(Optional.empty());
        given(aliasRepository.findAllByProjectIntegrationIdAndAliasTypeAndAliasValue(
                2L,
                IntegrationIdentityAliasType.LOGIN,
                ProviderActorKey.googleProviderIdAlias("permission-1")
        )).willReturn(List.of(ProjectMemberIntegrationIdentityAlias.builder()
                .identity(identity)
                .build()));

        ProjectMember resolved = integrationActorMappingService.resolve(
                integration, "permission-1", "유상완", null
        );

        assertThat(resolved).isSameAs(member);
    }
}
