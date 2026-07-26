package com.plog.domain.integration.service;

import com.plog.domain.integration.entity.IntegrationIdentityAliasType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.ProjectMemberIntegrationIdentityAliasRepository;
import com.plog.domain.integration.repository.ProjectMemberIntegrationIdentityRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 명시적 provider ID, 고유 별칭, 고유 사용자 이메일 순서로 활동 actor를 프로젝트 멤버에 매핑한다. */
@Service
@RequiredArgsConstructor
public class IntegrationActorMappingService {

    private final ProjectMemberIntegrationIdentityRepository identityRepository;
    private final ProjectMemberIntegrationIdentityAliasRepository aliasRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public ProjectMember resolve(ProjectIntegration integration, String actorProviderId, String actorLogin, String actorEmail) {
        if (actorProviderId != null && !actorProviderId.isBlank()) {
            ProjectMember exact = identityRepository.findByProjectIntegrationIdAndProviderActorId(
                    integration.getId(), actorProviderId).map(identity -> identity.getProjectMember()).orElse(null);
            if (exact != null) {
                return exact;
            }
        }
        ProjectMember emailAlias = resolveAlias(integration.getId(), IntegrationIdentityAliasType.EMAIL, actorEmail);
        if (emailAlias != null) {
            return emailAlias;
        }
        ProjectMember loginAlias = resolveAlias(integration.getId(), IntegrationIdentityAliasType.LOGIN, actorLogin);
        if (loginAlias != null) {
            return loginAlias;
        }
        return resolveUniqueProjectEmail(integration.getProject().getId(), actorEmail);
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

    private ProjectMember resolveUniqueProjectEmail(Long projectId, String actorEmail) {
        if (actorEmail == null || actorEmail.isBlank()) {
            return null;
        }
        List<ProjectMember> matches = projectMemberRepository.findAllByProjectIdAndStatusAndUserEmailIgnoreCase(
                projectId, MemberStatus.ACTIVE, actorEmail
        );
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private String normalize(IntegrationIdentityAliasType type, String value) {
        return type == IntegrationIdentityAliasType.EMAIL ? value.toLowerCase(Locale.ROOT) : value;
    }
}
