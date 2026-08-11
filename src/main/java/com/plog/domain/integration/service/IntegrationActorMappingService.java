package com.plog.domain.integration.service;

import com.plog.domain.integration.entity.IntegrationIdentityAliasType;
import com.plog.domain.integration.entity.LinkType;
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
            ProjectMember exactProviderId = resolveExact(integration.getId(), providerId.storageValue());
            if (exactProviderId != null || !isGoogle(integration.getLinkType())) {
                return exactProviderId;
            }
            ProjectMember providerIdAlias = resolveAliasValue(
                    integration.getId(),
                    IntegrationIdentityAliasType.LOGIN,
                    ProviderActorKey.googleProviderIdAlias(actorProviderId)
            );
            if (providerIdAlias != null) {
                return providerIdAlias;
            }
        }
        ProviderActorKey email = ProviderActorKey.email(actorEmail);
        ProjectMember exactEmail = email == null ? null : resolveExact(integration.getId(), email.storageValue());
        if (exactEmail != null) {
            return exactEmail;
        }
        ProjectMember emailAlias = resolveAlias(integration.getId(), IntegrationIdentityAliasType.EMAIL, actorEmail);
        if (emailAlias != null) {
            return emailAlias;
        }
        if (isGoogle(integration.getLinkType())) {
            return null;
        }
        ProviderActorKey login = ProviderActorKey.login(actorLogin);
        ProjectMember exactLogin = login == null ? null : resolveExact(integration.getId(), login.storageValue());
        if (exactLogin != null) {
            return exactLogin;
        }
        ProjectMember loginAlias = resolveAlias(integration.getId(), IntegrationIdentityAliasType.LOGIN, actorLogin);
        if (loginAlias != null) {
            return loginAlias;
        }
        return null;
    }

    private boolean isGoogle(LinkType linkType) {
        return linkType == LinkType.GOOGLE_DOCS || linkType == LinkType.GOOGLE_SLIDES;
    }

    private ProjectMember resolveExact(Long integrationId, String providerActorId) {
        return identityRepository.findByProjectIntegrationIdAndProviderActorId(integrationId, providerActorId)
                .map(identity -> identity.getProjectMember())
                .orElse(null);
    }

    private ProjectMember resolveAlias(Long integrationId, IntegrationIdentityAliasType type, String value) {
        return resolveAliasValue(integrationId, type, normalize(type, value));
    }

    private ProjectMember resolveAliasValue(
            Long integrationId,
            IntegrationIdentityAliasType type,
            String normalizedValue
    ) {
        if (normalizedValue == null || normalizedValue.isBlank()) {
            return null;
        }
        List<ProjectMember> members = aliasRepository
                .findAllByProjectIntegrationIdAndAliasTypeAndAliasValue(integrationId, type, normalizedValue)
                .stream()
                .map(alias -> alias.getIdentity().getProjectMember())
                .distinct()
                .toList();
        return members.size() == 1 ? members.get(0) : null;
    }

    private String normalize(IntegrationIdentityAliasType type, String value) {
        ProviderActorKey key = switch (type) {
            case EMAIL -> ProviderActorKey.email(value);
            case LOGIN -> ProviderActorKey.login(value);
        };
        return key == null ? null : key.value();
    }
}
