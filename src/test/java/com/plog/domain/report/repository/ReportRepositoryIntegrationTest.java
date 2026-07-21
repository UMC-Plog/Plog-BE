package com.plog.domain.report.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.ReportStatus;
import com.plog.domain.report.repository.projection.ReportSummary;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.jdbc.core.JdbcTemplate;
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
class ReportRepositoryIntegrationTest {

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
    private ReportRepository reportRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void returnsOnlyTheRequestedProjectsReportsAsAStableSliceProjection() {
        Project project = saveProject("Plog");
        Project otherProject = saveProject("Other");
        Report older = saveCompleted(project, LocalDateTime.of(2026, 7, 20, 10, 0));
        Report latest = saveCompleted(project, LocalDateTime.of(2026, 7, 21, 10, 0));
        Report generating = reportRepository.save(Report.start(project));
        saveCompleted(otherProject, LocalDateTime.of(2026, 7, 22, 10, 0));

        Slice<ReportSummary> first = reportRepository.findProjectReportSlice(
                project.getId(), PageRequest.of(0, 2)
        );
        Slice<ReportSummary> second = reportRepository.findProjectReportSlice(
                project.getId(), PageRequest.of(1, 2)
        );

        assertThat(first.hasNext()).isTrue();
        assertThat(first.getContent()).extracting(ReportSummary::getReportId)
                .containsExactly(latest.getId(), older.getId());
        assertThat(first.getContent().getFirst().getProjectId()).isEqualTo(project.getId());
        assertThat(first.getContent().getFirst().getProjectName()).isEqualTo("Plog");
        assertThat(first.getContent().getFirst().getReportStatus()).isEqualTo(ReportStatus.COMPLETED);
        assertThat(second.hasNext()).isFalse();
        assertThat(second.getContent()).extracting(ReportSummary::getReportId)
                .containsExactly(generating.getId());
    }

    @Test
    void databaseDefaultKeepsLegacyStyleRowsInGeneratingState() {
        Project project = saveProject("Legacy");
        jdbcTemplate.update(
                "insert into reports(project_id, created_at, updated_at) values (?, now(), now())",
                project.getId()
        );
        entityManager.clear();

        Report stored = reportRepository.findAll().getFirst();

        assertThat(stored.getStatus()).isEqualTo(ReportStatus.GENERATING);
        assertThat(stored.getCompletedAt()).isNull();
    }

    private Report saveCompleted(Project project, LocalDateTime completedAt) {
        Report report = Report.start(project);
        report.complete(completedAt);
        return reportRepository.save(report);
    }

    private Project saveProject(String name) {
        LocalDate today = LocalDate.of(2026, 7, 21);
        return projectRepository.save(Project.builder()
                .projectName(name)
                .inviteTokenHash(UUID.randomUUID().toString())
                .inviteTokenEncrypted("encrypted-" + name)
                .projectType(ProjectType.GENERAL)
                .status(ProjectStatus.IN_PROGRESS)
                .startDay(today)
                .endDay(today.plusDays(30))
                .build());
    }
}
