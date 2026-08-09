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
import com.plog.domain.report.entity.RawActivityType;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import com.plog.domain.report.service.ExternalActivityCompetencyMapper;
import com.plog.domain.report.service.IntegrationActivityReportLogAdapter;
import com.plog.domain.user.entity.User;
import com.plog.infrastructure.s3.UploadedFileService;
import java.time.Instant;
import java.time.LocalDate;
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
    void providerActorProjectionQueryReturnsClearedRowsByProviderIdAndFallbackAliases() {
        Project project = entityManager.persist(project("provider-projection"));
        ProjectMember currentMember = entityManager.persist(member(
                project,
                User.createLocal("provider-current@example.com", "encoded", "현재", "provider-current"),
                ProjectRole.OWNER
        ));
        ProjectIntegration integration = entityManager.persist(integration(project, currentMember));
        IntegrationResource resource = entityManager.persist(resource(integration, currentMember));
        entityManager.persist(activity(
                resource, currentMember, "event-provider", "123", "provider-login", "provider@example.com"));
        entityManager.persist(activity(
                resource, currentMember, "event-email", null, null, "WANTKDD@EXAMPLE.COM"));
        entityManager.persist(activity(
                resource, currentMember, "event-login", null, "WANTKDD", null));
        entityManager.persist(activity(
                resource, currentMember, "event-other-provider", "other", "WANTKDD", "WANTKDD@EXAMPLE.COM"));

        Project otherProject = entityManager.persist(project("provider-projection-other"));
        ProjectMember otherMember = entityManager.persist(member(
                otherProject,
                User.createLocal("provider-other@example.com", "encoded", "다른", "provider-other"),
                ProjectRole.OWNER
        ));
        ProjectIntegration otherIntegration = entityManager.persist(integration(otherProject, otherMember));
        IntegrationResource otherResource = entityManager.persist(resource(otherIntegration, otherMember));
        entityManager.persist(activity(
                otherResource, otherMember, "event-other-integration", "123", "WANTKDD", "WANTKDD@EXAMPLE.COM"));
        entityManager.flush();
        entityManager.clear();

        assertThat(activityRepository.clearProjectMemberByProviderId(
                integration.getId(), currentMember, "123")).isOne();
        assertThat(activityRepository.clearProjectMemberByEmail(
                integration.getId(), currentMember, "wantkdd@example.com")).isOne();
        assertThat(activityRepository.clearProjectMemberByLogin(
                integration.getId(), currentMember, "wantkdd")).isOne();
        entityManager.clear();

        var targets = activityRepository.findReportProjectionTargetsByProviderActor(
                integration.getId(), "123", "wantkdd", "wantkdd@example.com");

        assertThat(targets)
                .extracting(IntegrationActivity::getProviderEventKey)
                .containsExactlyInAnyOrder("event-provider", "event-email", "event-login");
        assertThat(targets).allSatisfy(target -> assertThat(target.getProjectMember()).isNull());
    }

    @Test
    void projectIntegrationProjectionQueryReturnsOnlyRequestedIntegrationRows() {
        Project project = entityManager.persist(project("integration-projection"));
        ProjectMember member = entityManager.persist(member(
                project,
                User.createLocal("integration-target@example.com", "encoded", "대상", "integration-target"),
                ProjectRole.OWNER
        ));
        ProjectIntegration integration = entityManager.persist(integration(project, member));
        IntegrationResource resource = entityManager.persist(resource(integration, member));
        entityManager.persist(activity(resource, member, "event-target-1", "actor-1", "actor-1@example.com"));
        entityManager.persist(activity(resource, null, "event-target-2", "actor-2", "actor-2@example.com"));

        Project otherProject = entityManager.persist(project("integration-projection-other"));
        ProjectMember otherMember = entityManager.persist(member(
                otherProject,
                User.createLocal("integration-control@example.com", "encoded", "대조", "integration-control"),
                ProjectRole.OWNER
        ));
        ProjectIntegration otherIntegration = entityManager.persist(integration(otherProject, otherMember));
        IntegrationResource otherResource = entityManager.persist(resource(otherIntegration, otherMember));
        entityManager.persist(activity(
                otherResource, otherMember, "event-control", "actor-control", "control@example.com"));
        entityManager.flush();
        entityManager.clear();

        assertThat(activityRepository.findReportProjectionTargetsByProjectIntegration(integration.getId()))
                .extracting(IntegrationActivity::getProviderEventKey)
                .containsExactlyInAnyOrder("event-target-1", "event-target-2");
    }

    @Test
    void persistedDuplicateWithMissingProviderTimestampProjectsOriginalRowUsingCreatedAt() {
        Project project = entityManager.persist(projectCoveringCurrentTimestamp("created-at-projection"));
        ProjectMember member = entityManager.persist(member(
                project,
                User.createLocal("created-at@example.com", "encoded", "생성시각", "created-at"),
                ProjectRole.OWNER
        ));
        ProjectIntegration integration = entityManager.persist(integration(project, member));
        IntegrationResource resource = entityManager.persist(resource(integration, member));
        entityManager.flush();

        assertThat(activityRepository.insertIfAbsent(
                resource.getId(), member.getId(), IntegrationActivityType.GITHUB_ISSUE.name(),
                "issue:duplicate", "actor-original", "original", "original@example.com",
                null, "https://github.com/Plog/backend/issues/1", "{\"state\":\"persisted\"}"
        )).isOne();
        assertThat(activityRepository.insertIfAbsent(
                resource.getId(), null, IntegrationActivityType.GITHUB_COMMIT.name(),
                "issue:duplicate", "actor-incoming", "incoming", "incoming@example.com",
                Instant.now(), "https://github.com/Plog/backend/commit/incoming", "{\"state\":\"incoming\"}"
        )).isZero();
        entityManager.clear();

        IntegrationActivity persisted = activityRepository.findReportProjectionTarget(
                resource.getId(), "issue:duplicate").orElseThrow();
        assertThat(persisted.getCreatedAt()).isNotNull();

        ObjectMapper objectMapper = new ObjectMapper();
        IntegrationActivityReportLogAdapter adapter = new IntegrationActivityReportLogAdapter(
                activityRepository,
                reportActivityLogRepository,
                new ExternalActivityCompetencyMapper(objectMapper),
                objectMapper);
        adapter.synchronizeActivity(resource.getId(), "issue:duplicate");
        entityManager.flush();
        entityManager.clear();

        assertThat(reportActivityLogRepository.findAll()).singleElement().satisfies(log -> {
            assertThat(log.getProjectMember().getId()).isEqualTo(member.getId());
            assertThat(log.getSourceDomain()).isEqualTo(SourceDomain.GITHUB);
            assertThat(log.getRawActivityType()).isEqualTo(RawActivityType.GITHUB_ISSUE);
            assertThat(log.getOccurredAt()).isEqualTo(persisted.getCreatedAt());
            assertThat(log.getMetadata()).isEqualToIgnoringWhitespace("{\"state\":\"persisted\"}");
        });
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

    private Project projectCoveringCurrentTimestamp(String name) {
        LocalDate utcToday = LocalDate.now(java.time.ZoneOffset.UTC);
        return Project.builder()
                .projectName(name)
                .inviteTokenHash(name + "-hash")
                .inviteTokenEncrypted(name + "-encrypted")
                .projectType(ProjectType.DEVELOP)
                .status(ProjectStatus.IN_PROGRESS)
                .startDay(utcToday.minusDays(1))
                .endDay(utcToday.plusDays(1))
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
