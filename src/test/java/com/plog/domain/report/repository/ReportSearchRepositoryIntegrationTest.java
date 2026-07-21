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
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.repository.projection.ReportSummary;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
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
class ReportSearchRepositoryIntegrationTest {

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
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void searchesOnlyActiveMembershipReportsByProjectNameAndCompletedRange() {
        User user = saveUser("search-owner");
        Project target = saveProject("PLOG API");
        addMember(user, target, MemberStatus.ACTIVE);
        Report older = saveCompleted(target, LocalDateTime.of(2026, 7, 20, 10, 0));
        Report latest = saveCompleted(target, LocalDateTime.of(2026, 7, 21, 10, 0));
        saveCompleted(target, LocalDateTime.of(2026, 6, 30, 10, 0));

        Project otherName = saveProject("Other");
        addMember(user, otherName, MemberStatus.ACTIVE);
        saveCompleted(otherName, LocalDateTime.of(2026, 7, 22, 10, 0));

        Project exited = saveProject("Plog exited");
        addMember(user, exited, MemberStatus.EXIT);
        saveCompleted(exited, LocalDateTime.of(2026, 7, 22, 10, 0));

        Project outsider = saveProject("Plog outsider");
        addMember(saveUser("search-outsider"), outsider, MemberStatus.ACTIVE);
        saveCompleted(outsider, LocalDateTime.of(2026, 7, 22, 10, 0));

        Slice<ReportSummary> first = reportRepository.searchAccessibleReportSlice(
                user.getId(),
                MemberStatus.ACTIVE,
                "%plog%",
                true,
                LocalDateTime.of(2026, 7, 1, 0, 0),
                true,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                PageRequest.of(0, 1)
        );
        Slice<ReportSummary> second = reportRepository.searchAccessibleReportSlice(
                user.getId(),
                MemberStatus.ACTIVE,
                "%plog%",
                true,
                LocalDateTime.of(2026, 7, 1, 0, 0),
                true,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                PageRequest.of(1, 1)
        );

        assertThat(first.hasNext()).isTrue();
        assertThat(first.getContent()).extracting(ReportSummary::getReportId)
                .containsExactly(latest.getId());
        assertThat(second.hasNext()).isFalse();
        assertThat(second.getContent()).extracting(ReportSummary::getReportId)
                .containsExactly(older.getId());
    }

    @Test
    void treatsLikeWildcardsAndEscapeCharacterAsProjectNameLiterals() {
        User user = saveUser("search-literal");
        Project literal = saveProject("rate_100%!");
        addMember(user, literal, MemberStatus.ACTIVE);
        Report expected = saveCompleted(literal, LocalDateTime.of(2026, 7, 20, 10, 0));
        Project wildcardMatch = saveProject("rateA100B!");
        addMember(user, wildcardMatch, MemberStatus.ACTIVE);
        saveCompleted(wildcardMatch, LocalDateTime.of(2026, 7, 21, 10, 0));

        Slice<ReportSummary> result = reportRepository.searchAccessibleReportSlice(
                user.getId(),
                MemberStatus.ACTIVE,
                "%rate!_100!%!!%",
                false,
                null,
                false,
                null,
                PageRequest.of(0, 20)
        );

        assertThat(result.getContent()).extracting(ReportSummary::getReportId)
                .containsExactly(expected.getId());
    }

    private Report saveCompleted(Project project, LocalDateTime completedAt) {
        Report report = Report.start(project);
        report.complete(completedAt);
        return reportRepository.save(report);
    }

    private void addMember(User user, Project project, MemberStatus status) {
        projectMemberRepository.save(ProjectMember.builder()
                .user(user)
                .project(project)
                .role(ProjectRole.MEMBER)
                .status(status)
                .build());
    }

    private Project saveProject(String name) {
        LocalDate today = LocalDate.of(2026, 7, 21);
        return projectRepository.save(Project.builder()
                .projectName(name)
                .inviteTokenHash(UUID.randomUUID().toString())
                .inviteTokenEncrypted("encrypted-" + UUID.randomUUID())
                .projectType(ProjectType.GENERAL)
                .status(ProjectStatus.IN_PROGRESS)
                .startDay(today)
                .endDay(today.plusDays(30))
                .build());
    }

    private User saveUser(String suffix) {
        return userRepository.save(User.createLocal(
                suffix + "@plog.test",
                "encoded-password",
                "Report User",
                "report-" + suffix
        ));
    }
}
