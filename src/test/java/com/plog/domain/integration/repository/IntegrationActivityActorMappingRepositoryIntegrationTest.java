package com.plog.domain.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.integration.entity.ActivityLog;
import com.plog.domain.integration.entity.ActivityType;
import com.plog.domain.integration.entity.ExternalConnection;
import com.plog.domain.integration.entity.ExternalResource;
import com.plog.domain.integration.entity.IntegrationActivity;
import com.plog.domain.integration.entity.IntegrationActivityType;
import com.plog.domain.integration.entity.IntegrationAuthorizationState;
import com.plog.domain.integration.entity.IntegrationCollectionRun;
import com.plog.domain.integration.entity.IntegrationCollectionRunStatus;
import com.plog.domain.integration.entity.IntegrationCredentialType;
import com.plog.domain.integration.entity.IntegrationIdentityAliasType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.IntegrationResourceStatus;
import com.plog.domain.integration.entity.IntegrationResourceType;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.entity.ProjectMemberIntegrationIdentity;
import com.plog.domain.integration.entity.ProjectMemberIntegrationIdentityAlias;
import com.plog.domain.integration.entity.ResourceType;
import com.plog.domain.notification.entity.Notification;
import com.plog.domain.notification.entity.NotificationType;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.service.ProjectPurgeService;
import com.plog.domain.user.entity.User;
import com.plog.infrastructure.s3.UploadedFileService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(ProjectPurgeService.class)
class IntegrationActivityActorMappingRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private IntegrationActivityRepository activityRepository;

    @Autowired
    private ProjectPurgeService projectPurgeService;

    @MockitoBean
    private UploadedFileService uploadedFileService;

    @Test
    void bulkMappingNeverOverwritesOrClearsAnotherMembersActivity() {
        Project project = entityManager.persist(project());
        ProjectMember currentMember = entityManager.persist(member(
                project,
                User.createLocal("current@example.com", "encoded", "현재", "current"),
                ProjectRole.OWNER
        ));
        ProjectMember anotherMember = entityManager.persist(member(
                project,
                User.createLocal("another@example.com", "encoded", "다른", "another"),
                ProjectRole.MEMBER
        ));
        ProjectIntegration integration = entityManager.persist(integration(project, currentMember));
        IntegrationResource resource = entityManager.persist(resource(integration, currentMember));
        IntegrationActivity unassigned = entityManager.persist(activity(
                resource, null, "event-unassigned", null, "shared@example.com"));
        IntegrationActivity ownedByAnother = entityManager.persist(activity(
                resource, anotherMember, "event-another", "actor-another", "shared@example.com"));
        entityManager.flush();
        entityManager.clear();

        assertThat(activityRepository.existsUnassignedActivityActorByProjectIntegrationId(integration.getId()))
                .isTrue();
        assertThat(activityRepository.assignProjectMemberByEmail(
                integration.getId(), currentMember, "shared@example.com"
        )).isOne();
        entityManager.clear();

        assertThat(activityRepository.existsUnassignedActivityActorByProjectIntegrationId(integration.getId()))
                .isFalse();
        assertThat(activityRepository.findById(unassigned.getId()).orElseThrow().getProjectMember().getId())
                .isEqualTo(currentMember.getId());
        assertThat(activityRepository.findById(ownedByAnother.getId()).orElseThrow().getProjectMember().getId())
                .isEqualTo(anotherMember.getId());

        assertThat(activityRepository.clearProjectMemberByEmail(
                integration.getId(), currentMember, "shared@example.com"
        )).isOne();
        entityManager.clear();

        assertThat(activityRepository.existsUnassignedActivityActorByProjectIntegrationId(integration.getId()))
                .isTrue();
        assertThat(activityRepository.findById(unassigned.getId()).orElseThrow().getProjectMember()).isNull();
        assertThat(activityRepository.findById(ownedByAnother.getId()).orElseThrow().getProjectMember().getId())
                .isEqualTo(anotherMember.getId());
    }

    @Test
    void actorObservationsNormalizeEmailAndLoginBeforeGrouping() {
        Project project = entityManager.persist(project());
        ProjectMember member = entityManager.persist(member(
                project,
                User.createLocal("normalize@example.com", "encoded", "정규화", "normalize"),
                ProjectRole.OWNER
        ));
        ProjectIntegration integration = entityManager.persist(integration(project, member));
        IntegrationResource resource = entityManager.persist(resource(integration, member));
        entityManager.persist(activity(resource, null, "event-upper", null, "Actor@Example.com"));
        entityManager.persist(activity(resource, null, "event-lower", null, "actor@example.com"));
        entityManager.flush();
        entityManager.clear();

        var observations = activityRepository.findActorObservations(integration.getId());

        assertThat(observations).singleElement().satisfies(observation -> {
            assertThat(observation.getActorLogin()).isEqualTo("actor@example.com");
            assertThat(observation.getActorEmail()).isEqualTo("actor@example.com");
            assertThat(observation.getActivityCount()).isEqualTo(2L);
        });
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", textBlock = """
            provider-actor,NULL,NULL
            NULL,actor-login,NULL
            NULL,NULL,actor@example.com
            """)
    void existsUnassignedActivityActorByProjectIntegrationIdReturnsTrueWhenAnyActorSnapshotIsNonblank(
            String actorProviderId,
            String actorLogin,
            String actorEmail
    ) {
        Project project = entityManager.persist(project());
        ProjectMember member = entityManager.persist(member(
                project,
                User.createLocal("unassigned@example.com", "encoded", "미매핑", "unassigned"),
                ProjectRole.OWNER
        ));
        ProjectIntegration integration = entityManager.persist(integration(project, member));
        IntegrationResource resource = entityManager.persist(resource(integration, member));
        entityManager.persist(activity(resource, null, "event-unassigned", actorProviderId, actorLogin, actorEmail));
        entityManager.flush();
        entityManager.clear();

        assertThat(activityRepository.existsUnassignedActivityActorByProjectIntegrationId(integration.getId()))
                .isTrue();
    }

    @Test
    void existsUnassignedActivityActorByProjectIntegrationIdReturnsFalseWhenUnassignedActorSnapshotIsBlank() {
        Project project = entityManager.persist(project());
        ProjectMember member = entityManager.persist(member(
                project,
                User.createLocal("blank@example.com", "encoded", "공백", "blank"),
                ProjectRole.OWNER
        ));
        ProjectIntegration integration = entityManager.persist(integration(project, member));
        IntegrationResource resource = entityManager.persist(resource(integration, member));
        entityManager.persist(activity(resource, null, "event-blank", " ", " ", " "));
        entityManager.flush();
        entityManager.clear();

        assertThat(activityRepository.existsUnassignedActivityActorByProjectIntegrationId(integration.getId()))
                .isFalse();
    }

    @Test
    void existsUnassignedActivityActorByProjectIntegrationIdIgnoresActorsOutsideRequestedIntegration() {
        Project targetProject = entityManager.persist(project());
        ProjectMember targetMember = entityManager.persist(member(
                targetProject,
                User.createLocal("target@example.com", "encoded", "대상", "target"),
                ProjectRole.OWNER
        ));
        ProjectIntegration targetIntegration = entityManager.persist(integration(targetProject, targetMember));

        Project otherProject = entityManager.persist(project("other"));
        ProjectMember otherMember = entityManager.persist(member(
                otherProject,
                User.createLocal("other@example.com", "encoded", "다른", "other"),
                ProjectRole.OWNER
        ));
        ProjectIntegration otherIntegration = entityManager.persist(integration(otherProject, otherMember));
        IntegrationResource otherResource = entityManager.persist(resource(otherIntegration, otherMember));
        entityManager.persist(activity(otherResource, null, "event-other", "provider-other", null, null));
        entityManager.flush();
        entityManager.clear();

        assertThat(activityRepository.existsUnassignedActivityActorByProjectIntegrationId(targetIntegration.getId()))
                .isFalse();
        assertThat(activityRepository.existsUnassignedActivityActorByProjectIntegrationId(otherIntegration.getId()))
                .isTrue();
    }

    @Test
    void existsUnassignedActivityActorByProjectIntegrationIdIgnoresAlreadyAssignedActors() {
        Project project = entityManager.persist(project());
        ProjectMember member = entityManager.persist(member(
                project,
                User.createLocal("assigned@example.com", "encoded", "매핑", "assigned"),
                ProjectRole.OWNER
        ));
        ProjectIntegration integration = entityManager.persist(integration(project, member));
        IntegrationResource resource = entityManager.persist(resource(integration, member));
        entityManager.persist(activity(resource, member, "event-assigned", "provider-assigned", null, null));
        entityManager.flush();
        entityManager.clear();

        assertThat(activityRepository.existsUnassignedActivityActorByProjectIntegrationId(integration.getId()))
                .isFalse();
    }

    @Test
    void projectPurgeDeletesLegacyAndCurrentIntegrationRowsBeforeMembers() {
        Project project = entityManager.persist(project());
        User user = User.createLocal("purge@example.com", "encoded", "정리", "purge");
        ProjectMember member = entityManager.persist(member(
                project,
                user,
                ProjectRole.OWNER
        ));
        ExternalConnection legacyConnection = entityManager.persist(ExternalConnection.builder()
                .projectMember(member)
                .linkType(LinkType.GITHUB)
                .externalAccountId("legacy-account")
                .build());
        ExternalResource legacyResource = entityManager.persist(ExternalResource.builder()
                .connection(legacyConnection)
                .resourceType(ResourceType.REPOSITORY)
                .externalResourceId("legacy-repository")
                .resourceName("legacy")
                .build());
        entityManager.persist(ActivityLog.builder()
                .resource(legacyResource)
                .projectMember(member)
                .activityType(ActivityType.COMMIT)
                .occurredAt(LocalDateTime.of(2026, 7, 28, 0, 0))
                .externalId("legacy-event")
                .externalAuthor("legacy-actor")
                .build());

        ProjectIntegration integration = entityManager.persist(integration(project, member));
        IntegrationResource resource = entityManager.persist(resource(integration, member));
        entityManager.persist(activity(resource, member, "current-event", "current-actor", null));
        ProjectMemberIntegrationIdentity identity = entityManager.persist(
                ProjectMemberIntegrationIdentity.builder()
                        .projectIntegration(integration)
                        .projectMember(member)
                        .providerActorId("current-actor")
                        .build()
        );
        entityManager.persist(ProjectMemberIntegrationIdentityAlias.builder()
                .identity(identity)
                .projectIntegration(integration)
                .aliasType(IntegrationIdentityAliasType.LOGIN)
                .aliasValue("current-login")
                .build());
        entityManager.persist(IntegrationAuthorizationState.builder()
                .project(project)
                .projectMember(member)
                .linkType(LinkType.GITHUB)
                .stateHash("a".repeat(64))
                .expiresAt(Instant.parse("2026-07-29T00:00:00Z"))
                .build());
        entityManager.persist(IntegrationCollectionRun.builder()
                .project(project)
                .status(IntegrationCollectionRunStatus.PENDING)
                .attemptCount(0)
                .build());
        entityManager.persist(Notification.create(
                user, project, NotificationType.CHAT_MENTION, "프로젝트 알림", null));
        entityManager.flush();

        projectPurgeService.purge(project.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(tableCount("activity_log")).isZero();
        assertThat(tableCount("external_resource")).isZero();
        assertThat(tableCount("external_connection")).isZero();
        assertThat(tableCount("integration_activities")).isZero();
        assertThat(tableCount("project_member_integration_identity_aliases")).isZero();
        assertThat(tableCount("project_member_integration_identities")).isZero();
        assertThat(tableCount("integration_resources")).isZero();
        assertThat(tableCount("integration_authorization_states")).isZero();
        assertThat(tableCount("integration_collection_runs")).isZero();
        assertThat(tableCount("project_integrations")).isZero();
        assertThat(tableCount("notifications")).isZero();
        assertThat(tableCount("project_members")).isZero();
    }

    private Project project() {
        return project("actor-mapping");
    }

    private Project project(String name) {
        LocalDate today = LocalDate.now();
        return Project.builder()
                .projectName(name)
                .inviteTokenHash(name + "-hash")
                .inviteTokenEncrypted(name + "-encrypted")
                .projectType(ProjectType.DEVELOP)
                .status(ProjectStatus.IN_PROGRESS)
                .startDay(today)
                .endDay(today.plusDays(30))
                .build();
    }

    private ProjectMember member(Project project, User user, ProjectRole role) {
        entityManager.persist(user);
        return ProjectMember.builder()
                .project(project)
                .user(user)
                .role(role)
                .status(MemberStatus.ACTIVE)
                .build();
    }

    private ProjectIntegration integration(Project project, ProjectMember connectedBy) {
        return ProjectIntegration.builder()
                .project(project)
                .connectedByProjectMember(connectedBy)
                .linkType(LinkType.GITHUB)
                .credentialType(IntegrationCredentialType.APP_INSTALLATION)
                .externalAccountId("organization-id")
                .externalAccountName("Plog")
                .providerConnectionId("installation-id")
                .build();
    }

    private IntegrationResource resource(ProjectIntegration integration, ProjectMember selectedBy) {
        return IntegrationResource.builder()
                .projectIntegration(integration)
                .selectedByProjectMember(selectedBy)
                .resourceType(IntegrationResourceType.GITHUB_REPOSITORY)
                .providerResourceId("repository-id")
                .resourceName("Plog/backend")
                .resourceUrl("https://github.com/Plog/backend")
                .resourceStatus(IntegrationResourceStatus.ACTIVE)
                .build();
    }

    private IntegrationActivity activity(
            IntegrationResource resource,
            ProjectMember projectMember,
            String eventKey,
            String actorProviderId,
            String actorEmail
    ) {
        return activity(resource, projectMember, eventKey, actorProviderId, actorEmail, actorEmail);
    }

    private IntegrationActivity activity(
            IntegrationResource resource,
            ProjectMember projectMember,
            String eventKey,
            String actorProviderId,
            String actorLogin,
            String actorEmail
    ) {
        return IntegrationActivity.builder()
                .integrationResource(resource)
                .projectMember(projectMember)
                .activityType(IntegrationActivityType.GITHUB_COMMIT)
                .providerEventKey(eventKey)
                .actorProviderId(actorProviderId)
                .actorLogin(actorLogin)
                .actorEmail(actorEmail)
                .occurredAt(Instant.parse("2026-07-28T00:00:00Z"))
                .providerPayload("{}")
                .build();
    }

    private long tableCount(String tableName) {
        return ((Number) entityManager.getEntityManager()
                .createNativeQuery("select count(*) from " + tableName)
                .getSingleResult()).longValue();
    }
}
