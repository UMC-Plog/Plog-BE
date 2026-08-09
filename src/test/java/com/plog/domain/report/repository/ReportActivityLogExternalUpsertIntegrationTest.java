package com.plog.domain.report.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.report.entity.RawActivityType;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReportActivityLogExternalUpsertIntegrationTest {

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
    private ReportActivityLogRepository activityLogRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void externalUpsertDoesNotUpdateIdenticalEventAndUpdatesChangedProjection() {
        Project project = saveProject();
        ProjectMember firstMember = saveMember(project, "first");
        ProjectMember secondMember = saveMember(project, "second");
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 1, 10, 15);
        String sourceRefId = "integration:40:GITHUB:resource:event";

        int inserted = activityLogRepository.upsertExternalActivityLog(
                firstMember.getId(),
                SourceDomain.GITHUB.name(),
                RawActivityType.GITHUB_COMMIT.name(),
                null,
                occurredAt,
                "{\"sha\":\"abc123\"}",
                sourceRefId
        );
        flushAndClear();
        int unchanged = activityLogRepository.upsertExternalActivityLog(
                firstMember.getId(),
                SourceDomain.GITHUB.name(),
                RawActivityType.GITHUB_COMMIT.name(),
                null,
                occurredAt,
                "{\"sha\":\"abc123\"}",
                sourceRefId
        );
        flushAndClear();
        int updated = activityLogRepository.upsertExternalActivityLog(
                secondMember.getId(),
                SourceDomain.GITHUB.name(),
                RawActivityType.GITHUB_COMMIT.name(),
                null,
                occurredAt,
                "{\"sha\":\"def456\"}",
                sourceRefId
        );
        flushAndClear();

        assertThat(inserted).isEqualTo(1);
        assertThat(unchanged).isZero();
        assertThat(updated).isEqualTo(1);

        assertThat(activityLogRepository.findAll()).hasSize(1);
        ReportActivityLog saved = activityLogRepository.findAll().get(0);
        assertThat(saved.getProjectMember().getId()).isEqualTo(secondMember.getId());
        assertThat(saved.getMetadata()).isEqualTo("{\"sha\":\"def456\"}");
    }

    @Test
    void externalDeleteRemovesProjectedActivityLogBySourceKey() {
        Project project = saveProject();
        ProjectMember member = saveMember(project, "member");
        String sourceRefId = "integration:40:GITHUB:resource:event";
        activityLogRepository.upsertExternalActivityLog(
                member.getId(),
                SourceDomain.GITHUB.name(),
                RawActivityType.GITHUB_COMMIT.name(),
                null,
                LocalDateTime.of(2026, 8, 1, 10, 15),
                "{}",
                sourceRefId
        );
        flushAndClear();

        int deleted = activityLogRepository.deleteExternalActivityLog(SourceDomain.GITHUB.name(), sourceRefId);
        flushAndClear();

        assertThat(deleted).isEqualTo(1);
        assertThat(activityLogRepository.findAll()).isEmpty();
    }

    @Test
    void memberPrefixDeleteIsScopedByGoogleDocsAndSlidesSourceRefPrefix() {
        Project project = saveProject();
        ProjectMember member = saveMember(project, "member");
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 1, 10, 15);
        String docsSourceRefId = "integration:40:GOOGLE_DOCS:resource:event";
        String slidesSourceRefId = "integration:40:GOOGLE_SLIDES:resource:event";
        activityLogRepository.upsertExternalActivityLog(
                member.getId(),
                SourceDomain.GOOGLE.name(),
                RawActivityType.GOOGLE_DRIVE_COMMENT.name(),
                null,
                occurredAt,
                "{}",
                docsSourceRefId
        );
        activityLogRepository.upsertExternalActivityLog(
                member.getId(),
                SourceDomain.GOOGLE.name(),
                RawActivityType.GOOGLE_DRIVE_COMMENT.name(),
                null,
                occurredAt,
                "{}",
                slidesSourceRefId
        );
        flushAndClear();

        int deleted = activityLogRepository.deleteExternalActivityLogsByMemberAndSourcePrefix(
                member.getId(),
                SourceDomain.GOOGLE.name(),
                "integration:40:GOOGLE_DOCS:"
        );
        flushAndClear();

        assertThat(deleted).isEqualTo(1);
        assertThat(activityLogRepository.findAll())
                .extracting(ReportActivityLog::getSourceRefId)
                .containsExactly(slidesSourceRefId);
    }

    @Test
    void sourcePrefixDeleteRemovesEveryMemberOnlyWithinRequestedResource() {
        Project project = saveProject();
        ProjectMember firstMember = saveMember(project, "first-resource");
        ProjectMember secondMember = saveMember(project, "second-resource");
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 1, 10, 15);
        String targetPrefix = "integration:40:FIGMA:target-resource:";
        String otherSourceRefId = "integration:40:FIGMA:other-resource:event";
        activityLogRepository.upsertExternalActivityLog(
                firstMember.getId(), SourceDomain.FIGMA.name(), RawActivityType.FIGMA_COMMENT.name(),
                null, occurredAt, "{}", targetPrefix + "event-1");
        activityLogRepository.upsertExternalActivityLog(
                secondMember.getId(), SourceDomain.FIGMA.name(), RawActivityType.FIGMA_COMMENT.name(),
                null, occurredAt, "{}", targetPrefix + "event-2");
        activityLogRepository.upsertExternalActivityLog(
                firstMember.getId(), SourceDomain.FIGMA.name(), RawActivityType.FIGMA_COMMENT.name(),
                null, occurredAt, "{}", otherSourceRefId);
        flushAndClear();

        int deleted = activityLogRepository.deleteExternalActivityLogsBySourcePrefix(
                SourceDomain.FIGMA.name(), targetPrefix);
        flushAndClear();

        assertThat(deleted).isEqualTo(2);
        assertThat(activityLogRepository.findAll())
                .extracting(ReportActivityLog::getSourceRefId)
                .containsExactly(otherSourceRefId);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private ProjectMember saveMember(Project project, String suffix) {
        User user = userRepository.save(User.createLocal(
                suffix + "@plog.test", "encoded-password", "User " + suffix, "nickname-" + suffix));
        return projectMemberRepository.save(ProjectMember.builder()
                .project(project)
                .user(user)
                .role(ProjectRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .build());
    }

    private Project saveProject() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return projectRepository.save(Project.builder()
                .projectName("Project")
                .inviteTokenHash(UUID.randomUUID().toString())
                .inviteTokenEncrypted("encrypted")
                .projectType(ProjectType.GENERAL)
                .status(ProjectStatus.IN_PROGRESS)
                .startDay(today)
                .endDay(today.plusDays(30))
                .build());
    }
}
