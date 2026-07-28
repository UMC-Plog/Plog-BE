package com.plog.domain.integration.service;

import com.plog.domain.integration.dto.request.IntegrationActorMappingRequest;
import com.plog.domain.integration.dto.response.IntegrationActorMappingListResponse;
import com.plog.domain.integration.dto.response.IntegrationActorMappingResponse;
import com.plog.domain.integration.dto.response.IntegrationProviderActorResponse;
import com.plog.domain.integration.entity.IntegrationIdentityAliasType;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.entity.ProjectMemberIntegrationIdentity;
import com.plog.domain.integration.entity.ProjectMemberIntegrationIdentityAlias;
import com.plog.domain.integration.repository.IntegrationActivityRepository;
import com.plog.domain.integration.repository.IntegrationActorObservation;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.integration.repository.ProjectMemberIntegrationIdentityAliasRepository;
import com.plog.domain.integration.repository.ProjectMemberIntegrationIdentityRepository;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IntegrationActorMappingManagementService {

    private final ProjectRepository projectRepository;
    private final ProjectIntegrationRepository projectIntegrationRepository;
    private final ProjectMemberIntegrationIdentityRepository identityRepository;
    private final ProjectMemberIntegrationIdentityAliasRepository aliasRepository;
    private final IntegrationActivityRepository activityRepository;
    private final ProjectAccessService projectAccessService;

    @Transactional(readOnly = true)
    public IntegrationActorMappingListResponse getMappings(Long projectId, Long userId, LinkType linkType) {
        ProjectMember currentMember = requireActiveMember(projectId, userId);
        ProjectIntegration integration = requireConnectedIntegration(projectId, linkType);
        List<ProjectMemberIntegrationIdentity> identities =
                identityRepository.findAllByProjectIntegrationId(integration.getId());
        List<ProjectMemberIntegrationIdentityAlias> aliases =
                aliasRepository.findAllByProjectIntegrationId(integration.getId());

        List<IntegrationActorMappingResponse> mappings = identities.stream()
                .map(identity -> toMappingResponse(
                        identity,
                        identity.getProjectMember().getId().equals(currentMember.getId())
                ))
                .sorted(Comparator.comparing(IntegrationActorMappingResponse::projectMemberId))
                .toList();
        List<IntegrationProviderActorResponse> availableProviderActors = providerActors(integration.getId()).stream()
                .filter(providerActor -> !isMapped(providerActor, identities, aliases))
                .map(ProviderActor::toResponse)
                .sorted(Comparator
                        .comparing(IntegrationProviderActorResponse::lastOccurredAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(IntegrationProviderActorResponse::actorKey))
                .toList();

        return new IntegrationActorMappingListResponse(
                projectId,
                linkType,
                currentMember.getId(),
                mappings,
                availableProviderActors
        );
    }

    @Transactional
    public IntegrationActorMappingResponse saveMyMapping(
            Long projectId,
            Long userId,
            LinkType linkType,
            IntegrationActorMappingRequest request
    ) {
        ProjectMember currentMember = requireActiveMember(projectId, userId);
        ProjectIntegration integration = requireConnectedIntegrationForUpdate(projectId, linkType);
        ProviderActor providerActor = providerActors(integration.getId()).stream()
                .filter(item -> item.actorKey().equals(request.actorKey()))
                .findFirst()
                .orElseThrow(() -> new ApiException(IntegrationErrorCode.PROVIDER_ACTOR_NOT_FOUND));

        List<ProjectMemberIntegrationIdentity> identities =
                identityRepository.findAllByProjectIntegrationId(integration.getId());
        List<ProjectMemberIntegrationIdentityAlias> aliases =
                aliasRepository.findAllByProjectIntegrationId(integration.getId());
        List<ProjectMemberIntegrationIdentity> claimedIdentities = identities.stream()
                .filter(identity -> matches(identity, providerActor, aliases))
                .distinct()
                .toList();
        if (claimedIdentities.size() > 1) {
            throw new ApiException(IntegrationErrorCode.ACTOR_MAPPING_AMBIGUOUS);
        }
        if (claimedIdentities.size() == 1
                && !claimedIdentities.get(0).getProjectMember().getId().equals(currentMember.getId())) {
            throw new ApiException(IntegrationErrorCode.ACTOR_ALREADY_MAPPED);
        }

        ProjectMemberIntegrationIdentity identity = identityRepository
                .findByProjectIntegrationIdAndProjectMemberId(integration.getId(), currentMember.getId())
                .orElse(null);
        ActorIdentity oldActor = identity == null ? null : ActorIdentity.from(identity);
        ProviderActorKey primaryActorKey = ProviderActorKey.primary(
                providerActor.providerActorId(),
                providerActor.providerLogin(),
                providerActor.providerEmail()
        );
        String storedProviderActorId = primaryActorKey.storageValue();

        try {
            if (identity == null) {
                identity = identityRepository.saveAndFlush(ProjectMemberIntegrationIdentity.builder()
                        .projectIntegration(integration)
                        .projectMember(currentMember)
                        .providerActorId(storedProviderActorId)
                        .providerLogin(providerActor.providerLogin())
                        .providerEmail(providerActor.providerEmail())
                        .build());
            } else {
                aliasRepository.deleteAllByIdentityId(identity.getId());
                identity.updateActor(
                        storedProviderActorId,
                        providerActor.providerLogin(),
                        providerActor.providerEmail()
                );
                identityRepository.flush();
            }
            saveAliases(identity, integration, providerActor);
            aliasRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(IntegrationErrorCode.ACTOR_ALREADY_MAPPED, exception);
        }

        if (oldActor != null) {
            clearActivities(integration.getId(), currentMember, linkType, oldActor);
        }
        assignActivities(integration.getId(), currentMember, linkType, providerActor);
        return toMappingResponse(identity, true);
    }

    @Transactional
    public IntegrationActorMappingResponse removeMyMapping(Long projectId, Long userId, LinkType linkType) {
        ProjectMember currentMember = requireActiveMember(projectId, userId);
        ProjectIntegration integration = requireConnectedIntegrationForUpdate(projectId, linkType);
        ProjectMemberIntegrationIdentity identity = identityRepository
                .findByProjectIntegrationIdAndProjectMemberId(integration.getId(), currentMember.getId())
                .orElseThrow(() -> new ApiException(IntegrationErrorCode.ACTOR_MAPPING_NOT_FOUND));
        IntegrationActorMappingResponse response = toMappingResponse(identity, true);
        ActorIdentity actor = ActorIdentity.from(identity);

        aliasRepository.deleteAllByIdentityId(identity.getId());
        identityRepository.delete(identity);
        identityRepository.flush();
        clearActivities(integration.getId(), currentMember, linkType, actor);
        return response;
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

    private ProjectIntegration requireConnectedIntegrationForUpdate(Long projectId, LinkType linkType) {
        return projectIntegrationRepository.findByProjectIdAndLinkTypeForUpdate(projectId, linkType)
                .filter(ProjectIntegration::isConnected)
                .orElseThrow(() -> new ApiException(IntegrationErrorCode.PROJECT_INTEGRATION_NOT_FOUND));
    }

    private List<ProviderActor> providerActors(Long projectIntegrationId) {
        Map<String, MutableProviderActor> providerActors = new LinkedHashMap<>();
        for (IntegrationActorObservation observation : activityRepository.findActorObservations(projectIntegrationId)) {
            String actorKey = actorKey(
                    observation.getActorProviderId(),
                    observation.getActorLogin(),
                    observation.getActorEmail()
            );
            if (actorKey == null) {
                continue;
            }
            providerActors.computeIfAbsent(actorKey, MutableProviderActor::new).merge(observation);
        }
        return providerActors.values().stream().map(MutableProviderActor::toProviderActor).toList();
    }

    private boolean isMapped(
            ProviderActor providerActor,
            List<ProjectMemberIntegrationIdentity> identities,
            List<ProjectMemberIntegrationIdentityAlias> aliases
    ) {
        return identities.stream().anyMatch(identity -> matches(identity, providerActor, aliases));
    }

    private boolean matches(
            ProjectMemberIntegrationIdentity identity,
            ProviderActor providerActor,
            List<ProjectMemberIntegrationIdentityAlias> aliases
    ) {
        ProviderActorKey providerId = ProviderActorKey.providerId(providerActor.providerActorId());
        if (providerId != null) {
            return providerId.storageValue().equals(identity.getProviderActorId());
        }
        ProviderActorKey primaryKey = ProviderActorKey.primary(
                null, providerActor.providerLogin(), providerActor.providerEmail());
        if (primaryKey != null && primaryKey.storageValue().equals(identity.getProviderActorId())) {
            return true;
        }
        return aliases.stream()
                .filter(alias -> alias.getIdentity().getId().equals(identity.getId()))
                .anyMatch(alias -> switch (alias.getAliasType()) {
                    case EMAIL -> matchesAlias(alias.getAliasValue(),
                            ProviderActorKey.email(providerActor.providerEmail()));
                    case LOGIN -> matchesAlias(alias.getAliasValue(),
                            ProviderActorKey.login(providerActor.providerLogin()));
                });
    }

    private void saveAliases(
            ProjectMemberIntegrationIdentity identity,
            ProjectIntegration integration,
            ProviderActor providerActor
    ) {
        List<ProjectMemberIntegrationIdentityAlias> aliases = new ArrayList<>();
        ProviderActorKey email = ProviderActorKey.email(providerActor.providerEmail());
        if (email != null) {
            aliases.add(ProjectMemberIntegrationIdentityAlias.builder()
                    .identity(identity)
                    .projectIntegration(integration)
                    .aliasType(IntegrationIdentityAliasType.EMAIL)
                    .aliasValue(email.value())
                    .build());
        }
        ProviderActorKey login = ProviderActorKey.login(providerActor.providerLogin());
        if (integration.getLinkType() == LinkType.GITHUB && login != null) {
            aliases.add(ProjectMemberIntegrationIdentityAlias.builder()
                    .identity(identity)
                    .projectIntegration(integration)
                    .aliasType(IntegrationIdentityAliasType.LOGIN)
                    .aliasValue(login.value())
                    .build());
        }
        aliasRepository.saveAll(aliases);
    }

    private boolean matchesAlias(String aliasValue, ProviderActorKey key) {
        return key != null && aliasValue.equals(key.value());
    }

    private void assignActivities(
            Long integrationId,
            ProjectMember projectMember,
            LinkType linkType,
            ProviderActor actor
    ) {
        for (ProviderActorKey key : matchKeys(
                linkType,
                ProviderActorKey.providerId(actor.providerActorId()),
                actor.providerLogin(),
                actor.providerEmail()
        )) {
            switch (key.type()) {
                case PROVIDER_ID -> activityRepository.assignProjectMemberByProviderId(
                        integrationId, projectMember, key.value());
                case EMAIL -> activityRepository.assignProjectMemberByEmail(
                        integrationId, projectMember, key.value());
                case LOGIN -> activityRepository.assignProjectMemberByLogin(
                        integrationId, projectMember, key.value());
            }
        }
    }

    private void clearActivities(
            Long integrationId,
            ProjectMember expectedMember,
            LinkType linkType,
            ActorIdentity actor
    ) {
        ProviderActorKey storedKey = ProviderActorKey.fromStored(actor.storedProviderActorId());
        ProviderActorKey providerId = storedKey != null && storedKey.type() == ProviderActorKey.Type.PROVIDER_ID
                ? storedKey
                : null;
        for (ProviderActorKey key : matchKeys(
                linkType,
                providerId,
                actor.providerLogin(),
                actor.providerEmail()
        )) {
            switch (key.type()) {
                case PROVIDER_ID -> activityRepository.clearProjectMemberByProviderId(
                        integrationId, expectedMember, key.value());
                case EMAIL -> activityRepository.clearProjectMemberByEmail(
                        integrationId, expectedMember, key.value());
                case LOGIN -> activityRepository.clearProjectMemberByLogin(
                        integrationId, expectedMember, key.value());
            }
        }
    }

    private List<ProviderActorKey> matchKeys(
            LinkType linkType,
            ProviderActorKey providerId,
            String providerLogin,
            String providerEmail
    ) {
        List<ProviderActorKey> keys = new ArrayList<>();
        if (providerId != null) {
            keys.add(providerId);
        }
        ProviderActorKey email = ProviderActorKey.email(providerEmail);
        if (email != null) {
            keys.add(email);
        }
        if (linkType == LinkType.GITHUB || (providerId == null && email == null)) {
            ProviderActorKey login = ProviderActorKey.login(providerLogin);
            if (login != null) {
                keys.add(login);
            }
        }
        return keys;
    }

    private IntegrationActorMappingResponse toMappingResponse(
            ProjectMemberIntegrationIdentity identity,
            boolean exposeProviderActorId
    ) {
        ProjectMember member = identity.getProjectMember();
        ProviderActorKey storedKey = ProviderActorKey.fromStored(identity.getProviderActorId());
        return new IntegrationActorMappingResponse(
                identity.getId(),
                member.getId(),
                member.getUser().getName(),
                member.getUser().getNickname(),
                member.getUser().getProfilePreset(),
                storedKey.selectionKey(),
                exposeProviderActorId ? storedKey.rawProviderId() : null,
                maskIfEmail(identity.getProviderLogin()),
                maskEmail(identity.getProviderEmail())
        );
    }

    private String actorKey(String providerActorId, String providerLogin, String providerEmail) {
        ProviderActorKey key = ProviderActorKey.primary(providerActorId, providerLogin, providerEmail);
        return key == null ? null : key.selectionKey();
    }

    private record ProviderActor(
            String actorKey,
            String providerActorId,
            String providerLogin,
            String providerEmail,
            long activityCount,
            Instant firstOccurredAt,
            Instant lastOccurredAt
    ) {
        IntegrationProviderActorResponse toResponse() {
            String maskedLogin = maskIfEmail(providerLogin);
            String maskedEmail = maskEmail(providerEmail);
            return new IntegrationProviderActorResponse(
                    actorKey,
                    null,
                    maskedLogin,
                    maskedEmail,
                    displayName(maskedLogin, maskedEmail, actorKey),
                    activityCount,
                    firstOccurredAt,
                    lastOccurredAt
            );
        }
    }

    private static final class MutableProviderActor {

        private final String actorKey;
        private String providerActorId;
        private String providerLogin;
        private String providerEmail;
        private long activityCount;
        private Instant firstOccurredAt;
        private Instant lastOccurredAt;

        private MutableProviderActor(String actorKey) {
            this.actorKey = actorKey;
        }

        private void merge(IntegrationActorObservation observation) {
            providerActorId = prefer(providerActorId, observation.getActorProviderId());
            providerLogin = prefer(providerLogin, observation.getActorLogin());
            providerEmail = prefer(providerEmail, observation.getActorEmail());
            activityCount += observation.getActivityCount();
            firstOccurredAt = earlier(firstOccurredAt, observation.getFirstOccurredAt());
            lastOccurredAt = later(lastOccurredAt, observation.getLastOccurredAt());
        }

        private ProviderActor toProviderActor() {
            return new ProviderActor(
                    actorKey,
                    providerActorId,
                    providerLogin,
                    providerEmail,
                    activityCount,
                    firstOccurredAt,
                    lastOccurredAt
            );
        }

        private static String prefer(String current, String candidate) {
            return current == null || current.isBlank() ? candidate : current;
        }

        private static Instant earlier(Instant current, Instant candidate) {
            if (current == null) {
                return candidate;
            }
            return candidate == null || current.isBefore(candidate) ? current : candidate;
        }

        private static Instant later(Instant current, Instant candidate) {
            if (current == null) {
                return candidate;
            }
            return candidate == null || current.isAfter(candidate) ? current : candidate;
        }
    }

    private static String displayName(String providerLogin, String providerEmail, String actorKey) {
        if (providerLogin != null && !providerLogin.isBlank()) {
            return providerLogin;
        }
        if (providerEmail != null && !providerEmail.isBlank()) {
            return providerEmail;
        }
        return "provider 계정 " + actorKey.substring(Math.max(0, actorKey.length() - 8));
    }

    private static String maskIfEmail(String value) {
        return value != null && value.contains("@") ? maskEmail(value) : value;
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex == email.length() - 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    private record ActorIdentity(String storedProviderActorId, String providerLogin, String providerEmail) {
        private static ActorIdentity from(ProjectMemberIntegrationIdentity identity) {
            return new ActorIdentity(
                    identity.getProviderActorId(),
                    identity.getProviderLogin(),
                    identity.getProviderEmail()
            );
        }
    }
}
