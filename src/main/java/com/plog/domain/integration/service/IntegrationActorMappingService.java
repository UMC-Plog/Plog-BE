package com.plog.domain.integration.service;

import com.plog.domain.integration.entity.IntegrationIdentityAliasType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.ProjectMemberIntegrationIdentityAliasRepository;
import com.plog.domain.integration.repository.ProjectMemberIntegrationIdentityRepository;
import com.plog.domain.project.entity.ProjectMember;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 사용자가 직접 저장한 provider ID와 고유 별칭으로만 활동 actor를 프로젝트 멤버에 매핑한다. */
@Service
@RequiredArgsConstructor
public class IntegrationActorMappingService {

    private final ProjectMemberIntegrationIdentityRepository identityRepository;
    private final ProjectMemberIntegrationIdentityAliasRepository aliasRepository;

    public ProjectMember resolve(ProjectIntegration integration, String actorProviderId, String actorLogin, String actorEmail) {
        ProviderActorKey providerId = ProviderActorKey.providerId(actorProviderId);
        if (providerId != null) {
            return resolveExact(integration.getId(), providerId.storageValue());
        }
        ProviderActorKey email = ProviderActorKey.email(actorEmail);
        ProjectMember exactEmail = email == null ? null : resolveExact(integration.getId(), email.storageValue());
        if (exactEmail != null) {
            return exactEmail;
        }
        ProviderActorKey login = ProviderActorKey.login(actorLogin);
        ProjectMember exactLogin = login == null ? null : resolveExact(integration.getId(), login.storageValue());
        if (exactLogin != null) {
            return exactLogin;
        }
        ProjectMember emailAlias = resolveAlias(integration.getId(), IntegrationIdentityAliasType.EMAIL, actorEmail);
        if (emailAlias != null) {
            return emailAlias;
        }
        ProjectMember loginAlias = resolveAlias(integration.getId(), IntegrationIdentityAliasType.LOGIN, actorLogin);
        if (loginAlias != null) {
            return loginAlias;
        }
        return null;
    }

    private ProjectMember resolveExact(Long integrationId, String providerActorId) {
        return identityRepository.findByProjectIntegrationIdAndProviderActorId(integrationId, providerActorId)
                .map(identity -> identity.getProjectMember())
                .orElse(null);
    }

    private ProjectMember resolveAlias(Long integrationId, IntegrationIdentityAliasType type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        List<ProjectMember> members = aliasRepository
                .findAllByProjectIntegrationIdAndAliasTypeAndAliasValue(integrationId, type, normalize(type, value))
                .stream()
                .map(alias -> alias.getIdentity().getProjectMember())
                .distinct()
                .toList();
        return members.size() == 1 ? members.get(0) : null;
    }

    private String normalize(IntegrationIdentityAliasType type, String value) {
        ProviderActorKey key = type == IntegrationIdentityAliasType.EMAIL
                ? ProviderActorKey.email(value)
                : ProviderActorKey.login(value);
        return key == null ? null : key.value();
    }
}
