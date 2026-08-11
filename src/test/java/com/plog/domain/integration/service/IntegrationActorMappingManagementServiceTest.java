package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.report.service.IntegrationActivityReportLogAdapter;
import com.plog.domain.user.entity.User;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class IntegrationActorMappingManagementServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectIntegrationRepository projectIntegrationRepository;
    @Mock
    private ProjectMemberIntegrationIdentityRepository identityRepository;
    @Mock
    private ProjectMemberIntegrationIdentityAliasRepository aliasRepository;
    @Mock
    private IntegrationActivityRepository activityRepository;
    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private IntegrationActivityReportLogAdapter reportLogAdapter;

    private IntegrationActorMappingManagementService service;
    private ProjectMember currentMember;
    private ProjectIntegration integration;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new IntegrationActorMappingManagementService(
                projectRepository,
                projectIntegrationRepository,
                identityRepository,
                aliasRepository,
                activityRepository,
                projectAccessService,
                reportLogAdapter
        );
        User user = mock(User.class);
        currentMember = ProjectMember.builder().id(3L).user(user).build();
        project = mock(Project.class);
        integration = ProjectIntegration.builder()
                .id(5L)
                .linkType(LinkType.GITHUB)
                .providerConnectionId("installation-1")
                .build();
        given(projectRepository.existsById(1L)).willReturn(true);
        lenient().when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        lenient().when(project.isCompleted()).thenReturn(false);
        given(projectAccessService.requireActiveMember(1L, 10L)).willReturn(currentMember);
    }

    @Test
    void returnsProviderActorsThatHaveNotBeenExplicitlyMapped() {
        IntegrationActorObservation observation = observation("123", "wantkdd", "vana@example.com", 4L);
        given(projectIntegrationRepository.findByProjectIdAndLinkType(1L, LinkType.GITHUB))
                .willReturn(Optional.of(integration));
        given(identityRepository.findAllByProjectIntegrationId(5L)).willReturn(List.of());
        given(aliasRepository.findAllByProjectIntegrationId(5L)).willReturn(List.of());
        given(activityRepository.findActorObservations(5L)).willReturn(List.of(observation));

        IntegrationActorMappingListResponse response = service.getMappings(1L, 10L, LinkType.GITHUB);

        assertThat(response.currentProjectMemberId()).isEqualTo(3L);
        assertThat(response.availableProviderActors()).hasSize(1);
        assertThat(response.availableProviderActors().get(0).actorKey())
                .isEqualTo(ProviderActorKey.providerId("123").selectionKey());
        assertThat(response.availableProviderActors().get(0).providerActorId()).isNull();
        assertThat(response.availableProviderActors().get(0).providerEmail())
                .isEqualTo("v***@example.com");
        assertThat(response.availableProviderActors().get(0).activityCount()).isEqualTo(4L);
        assertThat(response.availableProviderActors().get(0).mapped()).isFalse();
    }

    @Test
    void hidesAnotherMembersRawProviderIdentifierAndEmail() {
        User anotherUser = mock(User.class);
        given(anotherUser.getName()).willReturn("김팀원");
        given(anotherUser.getNickname()).willReturn("팀원");
        ProjectMember anotherMember = ProjectMember.builder().id(4L).user(anotherUser).build();
        ProjectMemberIntegrationIdentity identity = ProjectMemberIntegrationIdentity.builder()
                .id(21L)
                .projectIntegration(integration)
                .projectMember(anotherMember)
                .providerActorId("provider-user-4")
                .providerLogin("teammate@example.com")
                .providerEmail("teammate@example.com")
                .build();
        given(projectIntegrationRepository.findByProjectIdAndLinkType(1L, LinkType.GITHUB))
                .willReturn(Optional.of(integration));
        given(identityRepository.findAllByProjectIntegrationId(5L)).willReturn(List.of(identity));
        given(aliasRepository.findAllByProjectIntegrationId(5L)).willReturn(List.of());
        IntegrationActorObservation actorObservation = observation(
                "provider-user-4", "teammate@example.com", "teammate@example.com", 2L);
        given(activityRepository.findActorObservations(5L)).willReturn(List.of(actorObservation));

        IntegrationActorMappingListResponse response = service.getMappings(1L, 10L, LinkType.GITHUB);

        IntegrationActorMappingResponse mapping = response.mappings().get(0);
        assertThat(mapping.providerActorId()).isNull();
        assertThat(mapping.providerLogin()).isEqualTo("t***@example.com");
        assertThat(mapping.providerEmail()).isEqualTo("t***@example.com");
        assertThat(mapping.actorKey())
                .isEqualTo(ProviderActorKey.providerId("provider-user-4").selectionKey());
        assertThat(response.availableProviderActors()).hasSize(1);
        assertThat(response.availableProviderActors().get(0).mapped()).isTrue();
        assertThat(response.availableProviderActors().get(0).mappedProjectMemberId()).isEqualTo(4L);
        assertThat(response.availableProviderActors().get(0).mappedByCurrentMember()).isFalse();
    }

    @Test
    void savesMySelectedActorAndBackfillsExistingActivities() {
        given(currentMember.getUser().getName()).willReturn("유상완");
        given(currentMember.getUser().getNickname()).willReturn("바나");
        IntegrationActorObservation observation = observation("123", "wantkdd", "vana@example.com", 4L);
        given(projectIntegrationRepository.findByProjectIdAndLinkTypeForUpdate(1L, LinkType.GITHUB))
                .willReturn(Optional.of(integration));
        given(activityRepository.findActorObservations(5L)).willReturn(List.of(observation));
        given(identityRepository.findAllByProjectIntegrationId(5L)).willReturn(List.of());
        given(aliasRepository.findAllByProjectIntegrationId(5L)).willReturn(List.of());
        given(identityRepository.findByProjectIntegrationIdAndProjectMemberId(5L, 3L))
                .willReturn(Optional.empty());
        given(identityRepository.saveAndFlush(any(ProjectMemberIntegrationIdentity.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        IntegrationActorMappingResponse response = service.saveMyMapping(
                1L,
                10L,
                LinkType.GITHUB,
                new IntegrationActorMappingRequest(ProviderActorKey.providerId("123").selectionKey())
        );

        assertThat(response.projectMemberId()).isEqualTo(3L);
        assertThat(response.actorKey()).isEqualTo(ProviderActorKey.providerId("123").selectionKey());
        verify(activityRepository).assignProjectMemberByProviderId(5L, currentMember, "123");
        verify(activityRepository).assignProjectMemberByEmail(5L, currentMember, "vana@example.com");
        verify(activityRepository).assignProjectMemberByLogin(5L, currentMember, "wantkdd");
        verify(reportLogAdapter).deleteProjectMemberProjection(1L, LinkType.GITHUB, 3L);
        verify(reportLogAdapter).synchronizeProjectMemberActivities(5L, 3L);
    }

    @Test
    void doesNotUseNonUniqueFigmaHandleAsAnAliasOrBulkMatcher() {
        integration = ProjectIntegration.builder()
                .id(5L)
                .linkType(LinkType.FIGMA)
                .providerConnectionId("figma-account")
                .build();
        given(currentMember.getUser().getName()).willReturn("유상완");
        given(currentMember.getUser().getNickname()).willReturn("바나");
        IntegrationActorObservation observation = observation("figma-user-1", "동명이인", null, 2L);
        given(projectIntegrationRepository.findByProjectIdAndLinkTypeForUpdate(1L, LinkType.FIGMA))
                .willReturn(Optional.of(integration));
        given(activityRepository.findActorObservations(5L)).willReturn(List.of(observation));
        given(identityRepository.findAllByProjectIntegrationId(5L)).willReturn(List.of());
        given(aliasRepository.findAllByProjectIntegrationId(5L)).willReturn(List.of());
        given(identityRepository.findByProjectIntegrationIdAndProjectMemberId(5L, 3L))
                .willReturn(Optional.empty());
        given(identityRepository.saveAndFlush(any(ProjectMemberIntegrationIdentity.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.saveMyMapping(
                1L,
                10L,
                LinkType.FIGMA,
                new IntegrationActorMappingRequest(
                        ProviderActorKey.providerId("figma-user-1").selectionKey())
        );

        verify(activityRepository).assignProjectMemberByProviderId(
                5L, currentMember, "figma-user-1");
        verify(activityRepository, never()).assignProjectMemberByLogin(any(), any(), any());
        verify(reportLogAdapter).deleteProjectMemberProjection(1L, LinkType.FIGMA, 3L);
        verify(reportLogAdapter).synchronizeProjectMemberActivities(5L, 3L);
        verify(aliasRepository).saveAll(List.of());
    }

    @Test
    void returnsGoogleDocsAndSlidesMappingsFromDistinctProjectIntegrations() {
        given(currentMember.getUser().getName()).willReturn("유상완");
        given(currentMember.getUser().getNickname()).willReturn("바나");
        ProjectIntegration docsIntegration = projectIntegration(20L, LinkType.GOOGLE_DOCS);
        ProjectIntegration slidesIntegration = projectIntegration(21L, LinkType.GOOGLE_SLIDES);
        IntegrationActorObservation docsObservation = observation(
                "docs-actor", "shared-google-name", "shared@example.com", 1L);
        IntegrationActorObservation slidesObservation = observation(
                "slides-actor", "shared-google-name", "shared@example.com", 1L);
        ProjectMemberIntegrationIdentity docsIdentity = ProjectMemberIntegrationIdentity.builder()
                .id(40L)
                .projectIntegration(docsIntegration)
                .projectMember(currentMember)
                .providerActorId("docs-actor")
                .providerLogin("shared-google-name")
                .providerEmail("shared@example.com")
                .build();
        ProjectMemberIntegrationIdentity slidesIdentity = ProjectMemberIntegrationIdentity.builder()
                .id(41L)
                .projectIntegration(slidesIntegration)
                .projectMember(currentMember)
                .providerActorId("slides-actor")
                .providerLogin("shared-google-name")
                .providerEmail("shared@example.com")
                .build();
        given(projectIntegrationRepository.findByProjectIdAndLinkType(1L, LinkType.GOOGLE_DOCS))
                .willReturn(Optional.of(docsIntegration));
        given(projectIntegrationRepository.findByProjectIdAndLinkType(1L, LinkType.GOOGLE_SLIDES))
                .willReturn(Optional.of(slidesIntegration));
        given(identityRepository.findAllByProjectIntegrationId(20L)).willReturn(List.of(docsIdentity));
        given(identityRepository.findAllByProjectIntegrationId(21L)).willReturn(List.of(slidesIdentity));
        given(aliasRepository.findAllByProjectIntegrationId(20L)).willReturn(List.of());
        given(aliasRepository.findAllByProjectIntegrationId(21L)).willReturn(List.of());
        given(activityRepository.findActorObservations(20L)).willReturn(List.of(docsObservation));
        given(activityRepository.findActorObservations(21L)).willReturn(List.of(slidesObservation));

        IntegrationActorMappingListResponse docsResponse = service.getMappings(1L, 10L, LinkType.GOOGLE_DOCS);
        IntegrationActorMappingListResponse slidesResponse = service.getMappings(1L, 10L, LinkType.GOOGLE_SLIDES);

        assertThat(docsResponse.linkType()).isEqualTo(LinkType.GOOGLE_DOCS);
        assertThat(docsResponse.mappings()).extracting(IntegrationActorMappingResponse::actorKey)
                .containsExactly(ProviderActorKey.providerId("docs-actor").selectionKey());
        assertThat(docsResponse.availableProviderActors())
                .extracting(providerActor -> providerActor.actorKey())
                .contains(ProviderActorKey.providerId("docs-actor").selectionKey())
                .doesNotContain(ProviderActorKey.providerId("slides-actor").selectionKey());
        assertThat(slidesResponse.linkType()).isEqualTo(LinkType.GOOGLE_SLIDES);
        assertThat(slidesResponse.mappings()).extracting(IntegrationActorMappingResponse::actorKey)
                .containsExactly(ProviderActorKey.providerId("slides-actor").selectionKey());
        assertThat(slidesResponse.availableProviderActors())
                .extracting(providerActor -> providerActor.actorKey())
                .contains(ProviderActorKey.providerId("slides-actor").selectionKey())
                .doesNotContain(ProviderActorKey.providerId("docs-actor").selectionKey());
    }

    @Test
    void savesGoogleDocsMappingAndAssignsOnlyGoogleDocsActivities() {
        given(currentMember.getUser().getName()).willReturn("유상완");
        given(currentMember.getUser().getNickname()).willReturn("바나");
        ProjectIntegration docsIntegration = projectIntegration(20L, LinkType.GOOGLE_DOCS);
        IntegrationActorObservation docsObservation = observation(
                "google-user-1", "shared-google-name", "shared@example.com", 2L);
        given(projectIntegrationRepository.findByProjectIdAndLinkTypeForUpdate(1L, LinkType.GOOGLE_DOCS))
                .willReturn(Optional.of(docsIntegration));
        given(activityRepository.findActorObservations(20L)).willReturn(List.of(docsObservation));
        given(identityRepository.findAllByProjectIntegrationId(20L)).willReturn(List.of());
        given(aliasRepository.findAllByProjectIntegrationId(20L)).willReturn(List.of());
        given(identityRepository.findByProjectIntegrationIdAndProjectMemberId(20L, 3L))
                .willReturn(Optional.empty());
        given(identityRepository.saveAndFlush(any(ProjectMemberIntegrationIdentity.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        IntegrationActorMappingResponse response = service.saveMyMapping(
                1L,
                10L,
                LinkType.GOOGLE_DOCS,
                new IntegrationActorMappingRequest(
                        ProviderActorKey.providerId("google-user-1").selectionKey())
        );

        assertThat(response.actorKey())
                .isEqualTo(ProviderActorKey.providerId("google-user-1").selectionKey());
        verify(activityRepository).findActorObservations(20L);
        verify(activityRepository).assignProjectMemberByProviderId(20L, currentMember, "google-user-1");
        verify(activityRepository).assignProjectMemberByEmail(20L, currentMember, "shared@example.com");
        verify(reportLogAdapter).deleteProjectMemberProjection(1L, LinkType.GOOGLE_DOCS, 3L);
        verify(reportLogAdapter).synchronizeProjectMemberActivities(20L, 3L);
        verifyNoMoreInteractions(activityRepository);
    }

    @Test
    void mergesGoogleActorsByProviderIdOrEmailAndAssignsWholeStrongIdentityCluster() {
        given(currentMember.getUser().getName()).willReturn("유상완");
        given(currentMember.getUser().getNickname()).willReturn("바나");
        ProjectIntegration docsIntegration = projectIntegration(20L, LinkType.GOOGLE_DOCS);
        IntegrationActorObservation peopleObservation = observation(
                "people/current", "유상완", "self@example.com", 2L);
        IntegrationActorObservation commentObservation = observation(
                "permission-current", "유상완", "SELF@example.com", 3L);
        IntegrationActorObservation emailOnlyObservation = observation(
                null, "유상완", "self@example.com", 1L);
        given(projectIntegrationRepository.findByProjectIdAndLinkTypeForUpdate(1L, LinkType.GOOGLE_DOCS))
                .willReturn(Optional.of(docsIntegration));
        given(activityRepository.findActorObservations(20L))
                .willReturn(List.of(commentObservation, peopleObservation, emailOnlyObservation));
        given(identityRepository.findAllByProjectIntegrationId(20L)).willReturn(List.of());
        given(aliasRepository.findAllByProjectIntegrationId(20L)).willReturn(List.of());
        given(identityRepository.findByProjectIntegrationIdAndProjectMemberId(20L, 3L))
                .willReturn(Optional.empty());
        given(identityRepository.saveAndFlush(any(ProjectMemberIntegrationIdentity.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        IntegrationActorMappingResponse response = service.saveMyMapping(
                1L,
                10L,
                LinkType.GOOGLE_DOCS,
                new IntegrationActorMappingRequest(
                        ProviderActorKey.providerId("people/current").selectionKey())
        );

        assertThat(response.actorKey())
                .isEqualTo(ProviderActorKey.providerId("people/current").selectionKey());
        verify(activityRepository).assignProjectMemberByProviderId(20L, currentMember, "people/current");
        verify(activityRepository).assignProjectMemberByProviderId(20L, currentMember, "permission-current");
        verify(activityRepository).assignProjectMemberByEmail(20L, currentMember, "self@example.com");
        verify(activityRepository, never()).assignProjectMemberByLogin(any(), any(), any());
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<List<ProjectMemberIntegrationIdentityAlias>> aliasesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(aliasRepository).saveAll(aliasesCaptor.capture());
        assertThat(aliasesCaptor.getValue())
                .extracting(alias -> alias.getAliasType() + ":" + alias.getAliasValue())
                .containsExactlyInAnyOrder(
                        "LOGIN:" + ProviderActorKey.googleProviderIdAlias("permission-current"),
                        "EMAIL:self@example.com"
                );
    }

    @Test
    void keepsSameGoogleDisplayNamesSeparateWhenStrongProviderIdsDiffer() {
        ProjectIntegration docsIntegration = projectIntegration(20L, LinkType.GOOGLE_DOCS);
        IntegrationActorObservation first = observation("people/first", "유상완", null, 1L);
        IntegrationActorObservation second = observation("people/second", "유상완", null, 1L);
        given(projectIntegrationRepository.findByProjectIdAndLinkType(1L, LinkType.GOOGLE_DOCS))
                .willReturn(Optional.of(docsIntegration));
        given(identityRepository.findAllByProjectIntegrationId(20L)).willReturn(List.of());
        given(aliasRepository.findAllByProjectIntegrationId(20L)).willReturn(List.of());
        given(activityRepository.findActorObservations(20L)).willReturn(List.of(first, second));

        IntegrationActorMappingListResponse response = service.getMappings(1L, 10L, LinkType.GOOGLE_DOCS);

        assertThat(response.availableProviderActors())
                .extracting(IntegrationProviderActorResponse::actorKey)
                .containsExactlyInAnyOrder(
                        ProviderActorKey.providerId("people/first").selectionKey(),
                        ProviderActorKey.providerId("people/second").selectionKey()
                );
    }

    @Test
    void excludesGoogleNameOnlyActorsFromMappingCandidates() {
        ProjectIntegration docsIntegration = projectIntegration(20L, LinkType.GOOGLE_DOCS);
        IntegrationActorObservation nameOnly = mock(IntegrationActorObservation.class);
        given(projectIntegrationRepository.findByProjectIdAndLinkType(1L, LinkType.GOOGLE_DOCS))
                .willReturn(Optional.of(docsIntegration));
        given(identityRepository.findAllByProjectIntegrationId(20L)).willReturn(List.of());
        given(aliasRepository.findAllByProjectIntegrationId(20L)).willReturn(List.of());
        given(activityRepository.findActorObservations(20L)).willReturn(List.of(nameOnly));

        IntegrationActorMappingListResponse response = service.getMappings(1L, 10L, LinkType.GOOGLE_DOCS);

        assertThat(response.availableProviderActors()).isEmpty();
    }

    @Test
    void rejectsGoogleNameOnlyActorSaveResolution() {
        ProjectIntegration docsIntegration = projectIntegration(20L, LinkType.GOOGLE_DOCS);
        IntegrationActorObservation nameOnly = mock(IntegrationActorObservation.class);
        given(projectIntegrationRepository.findByProjectIdAndLinkTypeForUpdate(1L, LinkType.GOOGLE_DOCS))
                .willReturn(Optional.of(docsIntegration));
        given(activityRepository.findActorObservations(20L)).willReturn(List.of(nameOnly));

        assertThatThrownBy(() -> service.saveMyMapping(
                1L,
                10L,
                LinkType.GOOGLE_DOCS,
                new IntegrationActorMappingRequest(
                        ProviderActorKey.login("유상완").selectionKey())
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(IntegrationErrorCode.PROVIDER_ACTOR_NOT_FOUND));
    }

    @Test
    void marksGoogleActorMappedWhenStoredIdentityMatchesItsStrongEmail() {
        ProjectIntegration docsIntegration = projectIntegration(20L, LinkType.GOOGLE_DOCS);
        ProjectMemberIntegrationIdentity emailIdentity = ProjectMemberIntegrationIdentity.builder()
                .id(42L)
                .projectIntegration(docsIntegration)
                .projectMember(currentMember)
                .providerActorId("email:self@example.com")
                .providerLogin("유상완")
                .providerEmail("self@example.com")
                .build();
        IntegrationActorObservation observation = observation(
                "people/current", "유상완", "SELF@example.com", 2L);
        given(projectIntegrationRepository.findByProjectIdAndLinkType(1L, LinkType.GOOGLE_DOCS))
                .willReturn(Optional.of(docsIntegration));
        given(identityRepository.findAllByProjectIntegrationId(20L)).willReturn(List.of(emailIdentity));
        given(aliasRepository.findAllByProjectIntegrationId(20L)).willReturn(List.of());
        given(activityRepository.findActorObservations(20L)).willReturn(List.of(observation));

        IntegrationActorMappingListResponse response = service.getMappings(1L, 10L, LinkType.GOOGLE_DOCS);

        assertThat(response.availableProviderActors()).singleElement().satisfies(actor -> {
            assertThat(actor.mapped()).isTrue();
            assertThat(actor.mappedProjectMemberId()).isEqualTo(3L);
        });
    }

    @Test
    void doesNotMatchGoogleActorToLegacyLoginIdentityWithSameDisplayName() {
        ProjectIntegration docsIntegration = projectIntegration(20L, LinkType.GOOGLE_DOCS);
        ProjectMemberIntegrationIdentity loginIdentity = ProjectMemberIntegrationIdentity.builder()
                .id(42L)
                .projectIntegration(docsIntegration)
                .projectMember(currentMember)
                .providerActorId("login:유상완")
                .providerLogin("유상완")
                .build();
        IntegrationActorObservation observation = observation(
                "people/other", "유상완", null, 2L);
        given(projectIntegrationRepository.findByProjectIdAndLinkType(1L, LinkType.GOOGLE_DOCS))
                .willReturn(Optional.of(docsIntegration));
        given(identityRepository.findAllByProjectIntegrationId(20L)).willReturn(List.of(loginIdentity));
        given(aliasRepository.findAllByProjectIntegrationId(20L)).willReturn(List.of());
        given(activityRepository.findActorObservations(20L)).willReturn(List.of(observation));

        IntegrationActorMappingListResponse response = service.getMappings(1L, 10L, LinkType.GOOGLE_DOCS);

        assertThat(response.availableProviderActors()).singleElement().satisfies(actor -> {
            assertThat(actor.mapped()).isFalse();
            assertThat(actor.mappedProjectMemberId()).isNull();
        });
    }

    @Test
    void removesGoogleDocsMappingAndClearsOnlyGoogleDocsActivities() {
        given(currentMember.getUser().getName()).willReturn("유상완");
        given(currentMember.getUser().getNickname()).willReturn("바나");
        ProjectIntegration docsIntegration = projectIntegration(20L, LinkType.GOOGLE_DOCS);
        ProjectMemberIntegrationIdentity identity = ProjectMemberIntegrationIdentity.builder()
                .id(42L)
                .projectIntegration(docsIntegration)
                .projectMember(currentMember)
                .providerActorId("google-user-1")
                .providerLogin("shared-google-name")
                .providerEmail("shared@example.com")
                .build();
        given(projectIntegrationRepository.findByProjectIdAndLinkTypeForUpdate(1L, LinkType.GOOGLE_DOCS))
                .willReturn(Optional.of(docsIntegration));
        given(identityRepository.findByProjectIntegrationIdAndProjectMemberId(20L, 3L))
                .willReturn(Optional.of(identity));

        IntegrationActorMappingResponse response = service.removeMyMapping(1L, 10L, LinkType.GOOGLE_DOCS);

        assertThat(response.mappingId()).isEqualTo(42L);
        verify(aliasRepository).deleteAllByIdentityId(42L);
        verify(identityRepository).delete(identity);
        verify(activityRepository).findActorObservations(20L);
        verify(activityRepository).clearProjectMemberByProviderId(20L, currentMember, "google-user-1");
        verify(activityRepository).clearProjectMemberByEmail(20L, currentMember, "shared@example.com");
        verify(reportLogAdapter).deleteProjectMemberProjection(1L, LinkType.GOOGLE_DOCS, 3L);
        verifyNoMoreInteractions(activityRepository);
    }

    @Test
    void removesGoogleMappingAndClearsWholeObservedStrongIdentityCluster() {
        given(currentMember.getUser().getName()).willReturn("유상완");
        given(currentMember.getUser().getNickname()).willReturn("바나");
        ProjectIntegration docsIntegration = projectIntegration(20L, LinkType.GOOGLE_DOCS);
        ProjectMemberIntegrationIdentity identity = ProjectMemberIntegrationIdentity.builder()
                .id(42L)
                .projectIntegration(docsIntegration)
                .projectMember(currentMember)
                .providerActorId("people/current")
                .providerLogin("유상완")
                .providerEmail("self@example.com")
                .build();
        IntegrationActorObservation peopleObservation = observation(
                "people/current", "유상완", "self@example.com", 2L);
        IntegrationActorObservation commentObservation = observation(
                "permission-current", "유상완", "SELF@example.com", 3L);
        given(projectIntegrationRepository.findByProjectIdAndLinkTypeForUpdate(1L, LinkType.GOOGLE_DOCS))
                .willReturn(Optional.of(docsIntegration));
        given(identityRepository.findByProjectIntegrationIdAndProjectMemberId(20L, 3L))
                .willReturn(Optional.of(identity));
        given(activityRepository.findActorObservations(20L))
                .willReturn(List.of(peopleObservation, commentObservation));

        service.removeMyMapping(1L, 10L, LinkType.GOOGLE_DOCS);

        verify(activityRepository).clearProjectMemberByProviderId(20L, currentMember, "people/current");
        verify(activityRepository).clearProjectMemberByProviderId(20L, currentMember, "permission-current");
        verify(activityRepository).clearProjectMemberByEmail(20L, currentMember, "self@example.com");
        verify(activityRepository, never()).clearProjectMemberByLogin(any(), any(), any());
    }

    @Test
    void remapsGoogleMappingAndClearsOldObservedStrongIdentityClusterBeforeAssigningNewActor() {
        given(currentMember.getUser().getName()).willReturn("유상완");
        given(currentMember.getUser().getNickname()).willReturn("바나");
        ProjectIntegration docsIntegration = projectIntegration(20L, LinkType.GOOGLE_DOCS);
        ProjectMemberIntegrationIdentity identity = ProjectMemberIntegrationIdentity.builder()
                .id(42L)
                .projectIntegration(docsIntegration)
                .projectMember(currentMember)
                .providerActorId("people/old")
                .providerLogin("유상완")
                .providerEmail("old@example.com")
                .build();
        IntegrationActorObservation oldPeople = observation("people/old", "유상완", "old@example.com", 2L);
        IntegrationActorObservation oldComment = observation("permission-old", "유상완", "OLD@example.com", 3L);
        IntegrationActorObservation newPeople = observation("people/new", "새계정", "new@example.com", 1L);
        given(projectIntegrationRepository.findByProjectIdAndLinkTypeForUpdate(1L, LinkType.GOOGLE_DOCS))
                .willReturn(Optional.of(docsIntegration));
        given(activityRepository.findActorObservations(20L))
                .willReturn(List.of(oldPeople, oldComment, newPeople));
        given(identityRepository.findAllByProjectIntegrationId(20L)).willReturn(List.of(identity));
        given(aliasRepository.findAllByProjectIntegrationId(20L)).willReturn(List.of());
        given(identityRepository.findByProjectIntegrationIdAndProjectMemberId(20L, 3L))
                .willReturn(Optional.of(identity));

        service.saveMyMapping(
                1L,
                10L,
                LinkType.GOOGLE_DOCS,
                new IntegrationActorMappingRequest(
                        ProviderActorKey.providerId("people/new").selectionKey())
        );

        verify(activityRepository).clearProjectMemberByProviderId(20L, currentMember, "people/old");
        verify(activityRepository).clearProjectMemberByProviderId(20L, currentMember, "permission-old");
        verify(activityRepository).clearProjectMemberByEmail(20L, currentMember, "old@example.com");
        verify(activityRepository).assignProjectMemberByProviderId(20L, currentMember, "people/new");
        verify(activityRepository).assignProjectMemberByEmail(20L, currentMember, "new@example.com");
        verify(activityRepository, never()).clearProjectMemberByLogin(any(), any(), any());
        verify(activityRepository, never()).assignProjectMemberByLogin(any(), any(), any());
    }

    @Test
    void rejectsProviderActorAlreadyMappedToAnotherProjectMember() {
        IntegrationActorObservation observation = observation("123", "wantkdd", null, 4L);
        ProjectMember anotherMember = ProjectMember.builder().id(4L).build();
        ProjectMemberIntegrationIdentity claimedIdentity = ProjectMemberIntegrationIdentity.builder()
                .id(21L)
                .projectIntegration(integration)
                .projectMember(anotherMember)
                .providerActorId("123")
                .build();
        given(projectIntegrationRepository.findByProjectIdAndLinkTypeForUpdate(1L, LinkType.GITHUB))
                .willReturn(Optional.of(integration));
        given(activityRepository.findActorObservations(5L))
                .willReturn(List.of(observation));
        given(identityRepository.findAllByProjectIntegrationId(5L)).willReturn(List.of(claimedIdentity));
        given(aliasRepository.findAllByProjectIntegrationId(5L)).willReturn(List.of());

        assertThatThrownBy(() -> service.saveMyMapping(
                1L,
                10L,
                LinkType.GITHUB,
                new IntegrationActorMappingRequest(ProviderActorKey.providerId("123").selectionKey())
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(IntegrationErrorCode.ACTOR_ALREADY_MAPPED));

        verify(identityRepository, never()).saveAndFlush(any(ProjectMemberIntegrationIdentity.class));
        verify(activityRepository, never()).assignProjectMemberByProviderId(any(), any(), any());
    }

    @Test
    void rejectsActorAliasMappedToMultipleProjectMembersAsAmbiguous() {
        IntegrationActorObservation observation = observation(
                null, null, "shared@example.com", 4L);
        ProjectMember firstMember = ProjectMember.builder().id(4L).build();
        ProjectMember secondMember = ProjectMember.builder().id(5L).build();
        ProjectMemberIntegrationIdentity firstIdentity = ProjectMemberIntegrationIdentity.builder()
                .id(21L)
                .projectIntegration(integration)
                .projectMember(firstMember)
                .providerActorId("first-provider-id")
                .build();
        ProjectMemberIntegrationIdentity secondIdentity = ProjectMemberIntegrationIdentity.builder()
                .id(22L)
                .projectIntegration(integration)
                .projectMember(secondMember)
                .providerActorId("second-provider-id")
                .build();
        ProjectMemberIntegrationIdentityAlias firstAlias = ProjectMemberIntegrationIdentityAlias.builder()
                .id(31L)
                .identity(firstIdentity)
                .projectIntegration(integration)
                .aliasType(IntegrationIdentityAliasType.EMAIL)
                .aliasValue("shared@example.com")
                .build();
        ProjectMemberIntegrationIdentityAlias secondAlias = ProjectMemberIntegrationIdentityAlias.builder()
                .id(32L)
                .identity(secondIdentity)
                .projectIntegration(integration)
                .aliasType(IntegrationIdentityAliasType.EMAIL)
                .aliasValue("shared@example.com")
                .build();
        given(projectIntegrationRepository.findByProjectIdAndLinkTypeForUpdate(1L, LinkType.GITHUB))
                .willReturn(Optional.of(integration));
        given(activityRepository.findActorObservations(5L)).willReturn(List.of(observation));
        given(identityRepository.findAllByProjectIntegrationId(5L))
                .willReturn(List.of(firstIdentity, secondIdentity));
        given(aliasRepository.findAllByProjectIntegrationId(5L))
                .willReturn(List.of(firstAlias, secondAlias));

        assertThatThrownBy(() -> service.saveMyMapping(
                1L,
                10L,
                LinkType.GITHUB,
                new IntegrationActorMappingRequest(
                        ProviderActorKey.email("shared@example.com").selectionKey())
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(
                        IntegrationErrorCode.ACTOR_MAPPING_AMBIGUOUS));

        verify(identityRepository, never()).saveAndFlush(any(ProjectMemberIntegrationIdentity.class));
        verify(activityRepository, never()).assignProjectMemberByEmail(any(), any(), any());
    }

    @Test
    void mapsExpectedActorUniqueConstraintToConflict() {
        prepareUnclaimedActorSave();
        DataIntegrityViolationException duplicateActor = constraintViolation(
                ProjectMemberIntegrationIdentity.UNIQUE_ACTOR_CONSTRAINT);
        given(identityRepository.saveAndFlush(any(ProjectMemberIntegrationIdentity.class)))
                .willThrow(duplicateActor);

        assertThatThrownBy(() -> service.saveMyMapping(
                1L,
                10L,
                LinkType.GITHUB,
                new IntegrationActorMappingRequest(
                        ProviderActorKey.providerId("123").selectionKey())
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(
                        IntegrationErrorCode.ACTOR_ALREADY_MAPPED));
    }

    @Test
    void propagatesUnexpectedIntegrityViolation() {
        prepareUnclaimedActorSave();
        DataIntegrityViolationException unexpected = constraintViolation(
                "fk_project_member_integration_identity");
        given(identityRepository.saveAndFlush(any(ProjectMemberIntegrationIdentity.class)))
                .willThrow(unexpected);

        assertThatThrownBy(() -> service.saveMyMapping(
                1L,
                10L,
                LinkType.GITHUB,
                new IntegrationActorMappingRequest(
                        ProviderActorKey.providerId("123").selectionKey())
        )).isSameAs(unexpected);
    }

    @Test
    void rejectsActorKeyThatWasNotObservedInCollectedActivities() {
        given(projectIntegrationRepository.findByProjectIdAndLinkTypeForUpdate(1L, LinkType.GITHUB))
                .willReturn(Optional.of(integration));
        given(activityRepository.findActorObservations(5L)).willReturn(List.of());

        assertThatThrownBy(() -> service.saveMyMapping(
                1L,
                10L,
                LinkType.GITHUB,
                new IntegrationActorMappingRequest("actor:unknown")
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(IntegrationErrorCode.PROVIDER_ACTOR_NOT_FOUND));
    }

    @Test
    void removesOnlyMyMappingAndClearsExistingActivityOwnership() {
        given(currentMember.getUser().getName()).willReturn("유상완");
        given(currentMember.getUser().getNickname()).willReturn("바나");
        ProjectMemberIntegrationIdentity identity = ProjectMemberIntegrationIdentity.builder()
                .id(20L)
                .projectIntegration(integration)
                .projectMember(currentMember)
                .providerActorId("123")
                .providerLogin("wantkdd")
                .providerEmail("vana@example.com")
                .build();
        given(projectIntegrationRepository.findByProjectIdAndLinkTypeForUpdate(1L, LinkType.GITHUB))
                .willReturn(Optional.of(integration));
        given(identityRepository.findByProjectIntegrationIdAndProjectMemberId(5L, 3L))
                .willReturn(Optional.of(identity));

        IntegrationActorMappingResponse response = service.removeMyMapping(1L, 10L, LinkType.GITHUB);

        assertThat(response.mappingId()).isEqualTo(20L);
        verify(aliasRepository).deleteAllByIdentityId(20L);
        verify(identityRepository).delete(identity);
        verify(activityRepository).clearProjectMemberByProviderId(5L, currentMember, "123");
        verify(activityRepository).clearProjectMemberByEmail(
                5L, currentMember, "vana@example.com");
        verify(activityRepository).clearProjectMemberByLogin(5L, currentMember, "wantkdd");
        verify(reportLogAdapter).deleteProjectMemberProjection(1L, LinkType.GITHUB, 3L);
    }

    @Test
    void rejectsSavingMyMappingAfterProjectCompletion() {
        Project completedProject = mock(Project.class);
        given(projectRepository.findById(1L)).willReturn(Optional.of(completedProject));
        given(completedProject.isCompleted()).willReturn(true);

        assertThatThrownBy(() -> service.saveMyMapping(
                1L,
                10L,
                LinkType.GITHUB,
                new IntegrationActorMappingRequest("actor:123")
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(IntegrationErrorCode.ACTOR_MAPPING_LOCKED));

        verify(projectIntegrationRepository, never())
                .findByProjectIdAndLinkTypeForUpdate(1L, LinkType.GITHUB);
    }

    @Test
    void rejectsRemovingMyMappingAfterProjectCompletion() {
        Project completedProject = mock(Project.class);
        given(projectRepository.findById(1L)).willReturn(Optional.of(completedProject));
        given(completedProject.isCompleted()).willReturn(true);

        assertThatThrownBy(() -> service.removeMyMapping(1L, 10L, LinkType.GITHUB))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(IntegrationErrorCode.ACTOR_MAPPING_LOCKED));

        verify(projectIntegrationRepository, never())
                .findByProjectIdAndLinkTypeForUpdate(1L, LinkType.GITHUB);
    }

    private IntegrationActorObservation observation(
            String actorProviderId,
            String actorLogin,
            String actorEmail,
            long activityCount
    ) {
        IntegrationActorObservation observation = mock(IntegrationActorObservation.class);
        given(observation.getActorProviderId()).willReturn(actorProviderId);
        given(observation.getActorLogin()).willReturn(actorLogin);
        given(observation.getActorEmail()).willReturn(actorEmail);
        given(observation.getActivityCount()).willReturn(activityCount);
        given(observation.getFirstOccurredAt()).willReturn(Instant.parse("2026-07-01T00:00:00Z"));
        given(observation.getLastOccurredAt()).willReturn(Instant.parse("2026-07-20T00:00:00Z"));
        return observation;
    }

    private void prepareUnclaimedActorSave() {
        IntegrationActorObservation observation = observation("123", "wantkdd", null, 1L);
        given(projectIntegrationRepository.findByProjectIdAndLinkTypeForUpdate(1L, LinkType.GITHUB))
                .willReturn(Optional.of(integration));
        given(activityRepository.findActorObservations(5L))
                .willReturn(List.of(observation));
        given(identityRepository.findAllByProjectIntegrationId(5L)).willReturn(List.of());
        given(aliasRepository.findAllByProjectIntegrationId(5L)).willReturn(List.of());
        given(identityRepository.findByProjectIntegrationIdAndProjectMemberId(5L, 3L))
                .willReturn(Optional.empty());
    }

    private ProjectIntegration projectIntegration(Long id, LinkType linkType) {
        return ProjectIntegration.builder()
                .id(id)
                .linkType(linkType)
                .providerConnectionId("connection-" + id)
                .build();
    }

    private DataIntegrityViolationException constraintViolation(String constraintName) {
        ConstraintViolationException cause = mock(ConstraintViolationException.class);
        given(cause.getConstraintName()).willReturn(constraintName);
        return new DataIntegrityViolationException("constraint violation", cause);
    }
}
