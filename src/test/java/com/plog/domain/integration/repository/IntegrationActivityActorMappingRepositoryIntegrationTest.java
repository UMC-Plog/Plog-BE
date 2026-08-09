package com.plog.domain.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.plog.domain.notification.entity.Notification;
import com.plog.domain.notification.entity.NotificationType;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.service.ProjectPurgeService;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import com.plog.domain.report.service.ExternalActivityCompetencyMapper;
import com.plog.domain.report.service.IntegrationActivityReportLogAdapter;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskCategory;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.user.entity.User;
import com.plog.infrastructure.s3.UploadedFileService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
    private ReportActivityLogRepository reportActivityLogRepository;

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

    @Test
    void backfillActorSnapshotByProviderIdFillsOnlyBlankActorFieldsInRequestedIntegration() {
        Project project = entityManager.persist(project());
        ProjectMember member = entityManager.persist(member(
                project,
                User.createLocal("backfill@example.com", "encoded", "보강", "backfill"),
                ProjectRole.OWNER
        ));
        ProjectIntegration integration = entityManager.persist(integration(project, member));
        IntegrationResource resource = entityManager.persist(resource(integration, member));
        IntegrationActivity missingBoth = entityManager.persist(activity(
                resource, null, "event-missing-both", "provider-actor", null, null));
        IntegrationActivity blankLogin = entityManager.persist(activity(
                resource, null, "event-blank-login", "provider-actor", " ", "existing@example.com"));
        IntegrationActivity blankEmail = entityManager.persist(activity(
                resource, null, "event-blank-email", "provider-actor", "existing-login", " "));
        IntegrationActivity existingSnapshot = entityManager.persist(activity(
                resource, null, "event-existing", "provider-actor", "kept-login", "kept@example.com"));
        IntegrationActivity otherActor = entityManager.persist(activity(
                resource, null, "event-other-actor", "other-actor", null, null));

        Project otherProject = entityManager.persist(project("other-backfill"));
        ProjectMember otherMember = entityManager.persist(member(
                otherProject,
                User.createLocal("other-backfill@example.com", "encoded", "다른", "other-backfill"),
                ProjectRole.OWNER
        ));
        ProjectIntegration otherIntegration = entityManager.persist(integration(otherProject, otherMember));
        IntegrationResource otherResource = entityManager.persist(resource(otherIntegration, otherMember));
        IntegrationActivity otherIntegrationActivity = entityManager.persist(activity(
                otherResource, null, "event-other-integration", "provider-actor", null, null));
        entityManager.flush();
        entityManager.clear();

        assertThat(activityRepository.backfillActorSnapshotByProviderId(
                integration.getId(), "provider-actor", "new-login", "new@example.com"
        )).isEqualTo(3);
        entityManager.clear();

        assertActorSnapshot(missingBoth.getId(), "new-login", "new@example.com");
        assertActorSnapshot(blankLogin.getId(), "new-login", "existing@example.com");
        assertActorSnapshot(blankEmail.getId(), "existing-login", "new@example.com");
        assertActorSnapshot(existingSnapshot.getId(), "kept-login", "kept@example.com");
        assertActorSnapshot(otherActor.getId(), null, null);
        assertActorSnapshot(otherIntegrationActivity.getId(), null, null);
    }

    @Test
    void upsertProviderPayloadIfChangedUpdatesGoogleDeletedStateWithoutDuplicatingActivity() {
        Project project = entityManager.persist(project());
        ProjectMember member = entityManager.persist(member(
                project,
                User.createLocal("google-comment@example.com", "encoded", "댓글", "google-comment"),
                ProjectRole.OWNER
        ));
        ProjectIntegration integration = entityManager.persist(integration(project, member));
        IntegrationResource resource = entityManager.persist(resource(integration, member));
        entityManager.flush();

        int inserted = activityRepository.upsertProviderPayloadIfChanged(
                resource.getId(), member.getId(), IntegrationActivityType.GOOGLE_DRIVE_COMMENT.name(),
                "comment:comment-1", "commenter-1", "Commenter", "commenter@example.com",
                Instant.parse("2026-08-01T12:00:00Z"), "https://docs.google.com/document/d/google-file-1/edit",
                "{\"id\":\"comment-1\",\"deleted\":false}"
        );
        entityManager.flush();
        entityManager.clear();

        Long activityId = activityId(resource.getId(), "comment:comment-1");
        IntegrationActivity active = activityRepository.findById(activityId).orElseThrow();
        assertThat(inserted).isOne();
        assertThat(active.getProviderPayload()).contains("\"deleted\":false");

        int unchanged = activityRepository.upsertProviderPayloadIfChanged(
                resource.getId(), member.getId(), IntegrationActivityType.GOOGLE_DRIVE_COMMENT.name(),
                "comment:comment-1", "commenter-1", "Commenter", "commenter@example.com",
                Instant.parse("2026-08-01T12:00:00Z"), "https://docs.google.com/document/d/google-file-1/edit",
                "{\"id\":\"comment-1\",\"deleted\":false}"
        );
        entityManager.flush();
        entityManager.clear();

        int updated = activityRepository.upsertProviderPayloadIfChanged(
                resource.getId(), null, IntegrationActivityType.GOOGLE_DRIVE_COMMENT.name(),
                "comment:comment-1", null, null, null,
                null, null,
                "{\"id\":\"comment-1\",\"deleted\":true}"
        );
        entityManager.flush();
        entityManager.clear();

        int deletedUnchanged = activityRepository.upsertProviderPayloadIfChanged(
                resource.getId(), null, IntegrationActivityType.GOOGLE_DRIVE_COMMENT.name(),
                "comment:comment-1", "changed-actor", "Changed", "changed@example.com",
                Instant.parse("2026-08-03T12:00:00Z"), "https://changed.example.com",
                "{\"id\":\"comment-1\",\"deleted\":true}"
        );
        entityManager.flush();
        entityManager.clear();

        IntegrationActivity deleted = activityRepository.findById(activityId).orElseThrow();
        assertThat(unchanged).isZero();
        assertThat(updated).isOne();
        assertThat(deletedUnchanged).isZero();
        assertThat(activityCount(resource.getId(), "comment:comment-1")).isOne();
        assertThat(deleted.getProviderPayload()).contains("\"deleted\":true");
        assertThat(deleted.getProjectMember().getId()).isEqualTo(member.getId());
        assertThat(deleted.getActorProviderId()).isEqualTo("commenter-1");
        assertThat(deleted.getActorLogin()).isEqualTo("Commenter");
        assertThat(deleted.getActorEmail()).isEqualTo("commenter@example.com");
        assertThat(deleted.getOccurredAt()).isEqualTo(Instant.parse("2026-08-01T12:00:00Z"));
        assertThat(deleted.getSourceUrl()).isEqualTo("https://docs.google.com/document/d/google-file-1/edit");
    }

    @Test
    void updateProviderPayloadIfChangedUpdatesOnlyAnExistingActivity() {
        Project project = entityManager.persist(project());
        ProjectMember member = entityManager.persist(member(
                project,
                User.createLocal("google-existing@example.com", "encoded", "기존 댓글", "google-existing"),
                ProjectRole.OWNER
        ));
        ProjectIntegration integration = entityManager.persist(integration(project, member));
        IntegrationResource resource = entityManager.persist(resource(integration, member));
        entityManager.flush();

        assertThat(activityRepository.updateProviderPayloadIfChanged(
                resource.getId(), "comment:missing", "{\"deleted\":true}"
        )).isZero();
        assertThat(activityCount(resource.getId(), "comment:missing")).isZero();

        activityRepository.upsertProviderPayloadIfChanged(
                resource.getId(), member.getId(), IntegrationActivityType.GOOGLE_DRIVE_COMMENT.name(),
                "comment:existing", "commenter-1", "Commenter", "commenter@example.com",
                Instant.parse("2026-08-01T12:00:00Z"),
                "https://docs.google.com/document/d/google-file-1/edit",
                "{\"deleted\":false}"
        );
        entityManager.flush();
        entityManager.clear();

        assertThat(activityRepository.updateProviderPayloadIfChanged(
                resource.getId(), "comment:existing", "{\"deleted\":true}"
        )).isOne();
        entityManager.clear();

        IntegrationActivity updated = activityRepository.findById(
                activityId(resource.getId(), "comment:existing")
        ).orElseThrow();
        assertThat(updated.getProviderPayload()).contains("\"deleted\":true");
        assertThat(updated.getActorProviderId()).isEqualTo("commenter-1");
        assertThat(updated.getActorLogin()).isEqualTo("Commenter");
        assertThat(updated.getActorEmail()).isEqualTo("commenter@example.com");
    }

    @Test
    void deletedGoogleCommentRemovesItsPersistedReportProjection() {
        Project project = entityManager.persist(project());
        ProjectMember member = entityManager.persist(member(
                project,
                User.createLocal("google-projection@example.com", "encoded", "댓글 투영", "google-projection"),
                ProjectRole.OWNER
        ));
        ProjectIntegration integration = entityManager.persist(ProjectIntegration.builder()
                .project(project)
                .connectedByProjectMember(member)
                .linkType(LinkType.GOOGLE_DOCS)
                .credentialType(IntegrationCredentialType.OAUTH)
                .externalAccountId("google-account")
                .externalAccountName("google@example.com")
                .providerConnectionId("google-connection")
                .build());
        IntegrationResource resource = entityManager.persist(IntegrationResource.builder()
                .projectIntegration(integration)
                .selectedByProjectMember(member)
                .resourceType(IntegrationResourceType.GOOGLE_DOCUMENT)
                .providerResourceId("google-document-1")
                .resourceName("문서")
                .resourceUrl("https://docs.google.com/document/d/google-document-1/edit")
                .resourceStatus(IntegrationResourceStatus.ACTIVE)
                .build());
        entityManager.persist(IntegrationActivity.builder()
                .integrationResource(resource)
                .projectMember(member)
                .activityType(IntegrationActivityType.GOOGLE_DRIVE_COMMENT)
                .providerEventKey("comment:google-comment-1")
                .actorProviderId("people/commenter-1")
                .actorLogin("Commenter")
                .actorEmail("commenter@example.com")
                .occurredAt(project.getStartDay().atTime(12, 0).toInstant(ZoneOffset.UTC))
                .providerPayload("{\"id\":\"google-comment-1\",\"deleted\":false}")
                .build());
        entityManager.flush();
        entityManager.clear();

        ObjectMapper objectMapper = new ObjectMapper();
        IntegrationActivityReportLogAdapter adapter = new IntegrationActivityReportLogAdapter(
                activityRepository,
                reportActivityLogRepository,
                new ExternalActivityCompetencyMapper(objectMapper),
                objectMapper
        );
        adapter.synchronizeActivity(resource.getId(), "comment:google-comment-1");
        entityManager.flush();
        assertThat(tableCount("report_activity_log")).isOne();

        assertThat(activityRepository.updateProviderPayloadIfChanged(
                resource.getId(),
                "comment:google-comment-1",
                "{\"id\":\"google-comment-1\",\"deleted\":true}"
        )).isOne();
        adapter.synchronizeActivity(resource.getId(), "comment:google-comment-1");
        entityManager.flush();

        assertThat(tableCount("report_activity_log")).isZero();
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
        createLegacyIntegrationTables();
        insertLegacyIntegrationRows(member.getId());

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
        Task task = entityManager.persist(Task.create(
                member, "purge linked task", TaskCategory.DEVELOP, TaskStatus.IN_PROGRESS, LocalDate.now()));
        insertReportActivityLog(null, null, "GITHUB", "GITHUB_COMMIT",
                "integration:" + project.getId() + ":GITHUB:nullable-member");
        insertReportActivityLog(member.getId(), null, "POST", "POST_CREATE", "post:purge");
        insertReportActivityLog(null, task.getId(), "TASK", "TASK_STATUS_CHANGE", "task:purge");
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
        assertThat(tableCount("report_activity_log")).isZero();
        assertThat(tableCount("notifications")).isZero();
        assertThat(tableCount("project_members")).isZero();
    }

    @Test
    void projectPurgeSucceedsWhenLegacyIntegrationTablesDoNotExist() {
        Project project = entityManager.persist(project());
        ProjectMember member = entityManager.persist(member(
                project,
                User.createLocal("purge-without-legacy@example.com", "encoded", "정리", "purge-without-legacy"),
                ProjectRole.OWNER
        ));
        entityManager.flush();
        entityManager.clear();
        dropLegacyIntegrationTables();

        assertThatCode(() -> projectPurgeService.purge(project.getId()))
                .doesNotThrowAnyException();

        assertThat(tableCount("project_members")).isZero();
        assertThat(tableCount("projects")).isZero();
    }

    @Test
    void projectPurgeDeletesLegacyExternalConnectionWhenChildTablesDoNotExist() {
        Project project = entityManager.persist(project());
        ProjectMember member = entityManager.persist(member(
                project,
                User.createLocal("purge-connection-only@example.com", "encoded", "정리", "purge-connection-only"),
                ProjectRole.OWNER
        ));
        createLegacyExternalConnectionTable();
        insertLegacyExternalConnection(member.getId());
        entityManager.flush();

        projectPurgeService.purge(project.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(tableCount("external_connection")).isZero();
        assertThat(tableCount("project_members")).isZero();
        assertThat(tableCount("projects")).isZero();
    }

    @Test
    void projectPurgeDeletesLegacyResourceWhenActivityTableDoesNotExist() {
        Project project = entityManager.persist(project());
        ProjectMember member = entityManager.persist(member(
                project,
                User.createLocal("purge-resource-only@example.com", "encoded", "정리", "purge-resource-only"),
                ProjectRole.OWNER
        ));
        createLegacyExternalConnectionTable();
        createLegacyExternalResourceTable();
        insertLegacyExternalConnection(member.getId());
        insertLegacyExternalResource();
        entityManager.flush();

        projectPurgeService.purge(project.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(tableCount("external_resource")).isZero();
        assertThat(tableCount("external_connection")).isZero();
        assertThat(tableCount("project_members")).isZero();
        assertThat(tableCount("projects")).isZero();
    }

    @Test
    void externalProjectionPrefixDeletesOnlyRequestedMemberOrProjectRows() {
        Project project = entityManager.persist(project());
        ProjectMember member = entityManager.persist(member(
                project,
                User.createLocal("projection-member@example.com", "encoded", "멤버", "projection-member"),
                ProjectRole.OWNER
        ));
        ProjectMember anotherMember = entityManager.persist(member(
                project,
                User.createLocal("projection-other@example.com", "encoded", "다른", "projection-other"),
                ProjectRole.MEMBER
        ));
        entityManager.flush();

        String figmaPrefix = "integration:" + project.getId() + ":FIGMA:";
        String githubPrefix = "integration:" + project.getId() + ":GITHUB:";
        insertReportActivityLog(member.getId(), null, "FIGMA", "FIGMA_COMMENT", figmaPrefix + "resource:event-1");
        insertReportActivityLog(anotherMember.getId(), null, "FIGMA", "FIGMA_COMMENT", figmaPrefix + "resource:event-2");
        insertReportActivityLog(member.getId(), null, "FIGMA", "FIGMA_COMMENT", githubPrefix + "resource:event-3");
        insertReportActivityLog(member.getId(), null, "GITHUB", "GITHUB_COMMIT", figmaPrefix + "resource:event-4");
        entityManager.flush();

        int memberDeleted = reportActivityLogRepository.deleteExternalActivityLogsByMemberAndSourcePrefix(
                member.getId(), SourceDomain.FIGMA.name(), figmaPrefix);
        entityManager.flush();

        assertThat(memberDeleted).isOne();
        assertThat(reportActivityCount("FIGMA", figmaPrefix + "resource:event-1")).isZero();
        assertThat(reportActivityCount("FIGMA", figmaPrefix + "resource:event-2")).isOne();
        assertThat(reportActivityCount("FIGMA", githubPrefix + "resource:event-3")).isOne();
        assertThat(reportActivityCount("GITHUB", figmaPrefix + "resource:event-4")).isOne();

        int projectDeleted = reportActivityLogRepository.deleteExternalActivityLogsBySourcePrefix(
                SourceDomain.FIGMA.name(), figmaPrefix);
        entityManager.flush();

        assertThat(projectDeleted).isOne();
        assertThat(reportActivityCount("FIGMA", figmaPrefix + "resource:event-2")).isZero();
        assertThat(reportActivityCount("FIGMA", githubPrefix + "resource:event-3")).isOne();
        assertThat(reportActivityCount("GITHUB", figmaPrefix + "resource:event-4")).isOne();
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

    private void insertReportActivityLog(
            Long projectMemberId,
            Long linkedTaskId,
            String sourceDomain,
            String rawActivityType,
            String sourceRefId
    ) {
        entityManager.getEntityManager()
                .createNativeQuery("""
                        insert into report_activity_log (
                            project_member_id,
                            linked_task_id,
                            source_domain,
                            raw_activity_type,
                            occurred_at,
                            metadata,
                            source_ref_id,
                            created_at,
                            updated_at
                        )
                        values (?1, ?2, ?3, ?4, current_timestamp, '{}'::jsonb, ?5,
                                current_timestamp, current_timestamp)
                        """)
                .setParameter(1, projectMemberId)
                .setParameter(2, linkedTaskId)
                .setParameter(3, sourceDomain)
                .setParameter(4, rawActivityType)
                .setParameter(5, sourceRefId)
                .executeUpdate();
    }

    private Long activityId(Long resourceId, String eventKey) {
        return ((Number) entityManager.getEntityManager()
                .createNativeQuery("""
                        select integration_activity_id
                        from integration_activities
                        where integration_resource_id = :resourceId
                          and provider_event_key = :eventKey
                        """)
                .setParameter("resourceId", resourceId)
                .setParameter("eventKey", eventKey)
                .getSingleResult()).longValue();
    }

    private long activityCount(Long resourceId, String eventKey) {
        return ((Number) entityManager.getEntityManager()
                .createNativeQuery("""
                        select count(*)
                        from integration_activities
                        where integration_resource_id = :resourceId
                          and provider_event_key = :eventKey
                        """)
                .setParameter("resourceId", resourceId)
                .setParameter("eventKey", eventKey)
                .getSingleResult()).longValue();
    }

    private long reportActivityCount(String sourceDomain, String sourceRefId) {
        return ((Number) entityManager.getEntityManager()
                .createNativeQuery("""
                        select count(*)
                        from report_activity_log
                        where source_domain = :sourceDomain
                          and source_ref_id = :sourceRefId
                        """)
                .setParameter("sourceDomain", sourceDomain)
                .setParameter("sourceRefId", sourceRefId)
                .getSingleResult()).longValue();
    }

    private void assertActorSnapshot(Long activityId, String actorLogin, String actorEmail) {
        IntegrationActivity activity = activityRepository.findById(activityId).orElseThrow();
        assertThat(activity.getActorLogin()).isEqualTo(actorLogin);
        assertThat(activity.getActorEmail()).isEqualTo(actorEmail);
    }

    private void createLegacyIntegrationTables() {
        createLegacyExternalConnectionTable();
        createLegacyExternalResourceTable();
        createLegacyActivityLogTable();
    }

    private void createLegacyExternalConnectionTable() {
        entityManager.getEntityManager().createNativeQuery("""
                create table if not exists external_connection (
                    connection_id bigint primary key,
                    created_at timestamp not null,
                    updated_at timestamp not null,
                    external_account_id varchar(255),
                    link_type varchar(255) not null,
                    project_member_id bigint not null references project_members(project_member_id)
                )
                """).executeUpdate();
    }

    private void createLegacyExternalResourceTable() {
        entityManager.getEntityManager().createNativeQuery("""
                create table if not exists external_resource (
                    resource_id bigint primary key,
                    created_at timestamp not null,
                    updated_at timestamp not null,
                    external_resource_id varchar(255) not null,
                    resource_name varchar(255),
                    resource_type varchar(255) not null,
                    sync_enabled boolean not null,
                    connection_id bigint not null references external_connection(connection_id)
                )
                """).executeUpdate();
    }

    private void createLegacyActivityLogTable() {
        entityManager.getEntityManager().createNativeQuery("""
                create table if not exists activity_log (
                    activity_id bigint primary key,
                    created_at timestamp not null,
                    updated_at timestamp not null,
                    activity_type varchar(255) not null,
                    external_author varchar(255) not null,
                    external_id varchar(255) not null,
                    occurred_at timestamp not null,
                    project_member_id bigint references project_members(project_member_id),
                    resource_id bigint not null references external_resource(resource_id)
                )
                """).executeUpdate();
    }

    private void insertLegacyIntegrationRows(Long projectMemberId) {
        insertLegacyExternalConnection(projectMemberId);
        insertLegacyExternalResource();
        insertLegacyActivityRows(projectMemberId);
    }

    private void insertLegacyExternalConnection(Long projectMemberId) {
        entityManager.getEntityManager().createNativeQuery("""
                insert into external_connection (
                    connection_id, created_at, updated_at, external_account_id, link_type, project_member_id
                ) values (
                    9001, current_timestamp, current_timestamp, 'legacy-account', 'GITHUB', :projectMemberId
                )
                """)
                .setParameter("projectMemberId", projectMemberId)
                .executeUpdate();
    }

    private void insertLegacyExternalResource() {
        entityManager.getEntityManager().createNativeQuery("""
                insert into external_resource (
                    resource_id, created_at, updated_at, external_resource_id,
                    resource_name, resource_type, sync_enabled, connection_id
                ) values (
                    9001, current_timestamp, current_timestamp, 'legacy-repository',
                    'legacy', 'REPOSITORY', true, 9001
                )
                """).executeUpdate();
    }

    private void insertLegacyActivityRows(Long projectMemberId) {
        entityManager.getEntityManager().createNativeQuery("""
                insert into activity_log (
                    activity_id, created_at, updated_at, activity_type, external_author,
                    external_id, occurred_at, project_member_id, resource_id
                ) values (
                    9001, current_timestamp, current_timestamp, 'COMMIT', 'legacy-actor',
                    'legacy-event', current_timestamp, :projectMemberId, 9001
                ), (
                    9002, current_timestamp, current_timestamp, 'COMMIT', 'legacy-unassigned-actor',
                    'legacy-unassigned-event', current_timestamp, null, 9001
                )
                """)
                .setParameter("projectMemberId", projectMemberId)
                .executeUpdate();
    }

    private void dropLegacyIntegrationTables() {
        entityManager.getEntityManager().createNativeQuery("drop table if exists activity_log").executeUpdate();
        entityManager.getEntityManager().createNativeQuery("drop table if exists external_resource").executeUpdate();
        entityManager.getEntityManager().createNativeQuery("drop table if exists external_connection").executeUpdate();
    }
}
