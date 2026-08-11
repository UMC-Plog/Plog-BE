package com.plog.domain.task.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskCategory;
import com.plog.domain.task.entity.TaskCompetencyClassification;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class TaskCompetencyRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired EntityManager entityManager;
    @Autowired TaskRepository taskRepository;

    @Test
    void persistsAndReadsTaskCompetencyColumns() {
        User user = User.createLocal("task-mapping@plog.test", "encoded-password", "테스터", "task-mapper");
        entityManager.persist(user);
        Project project = Project.builder().projectName("Plog").inviteTokenHash("task-mapping-hash")
                .inviteTokenEncrypted("task-mapping-encrypted").projectType(ProjectType.DEVELOP)
                .status(ProjectStatus.IN_PROGRESS).startDay(LocalDate.now())
                .endDay(LocalDate.now().plusDays(30)).build();
        entityManager.persist(project);
        ProjectMember member = ProjectMember.builder().user(user).project(project).role(ProjectRole.MEMBER)
                .status(MemberStatus.ACTIVE).build();
        entityManager.persist(member);

        Task task = Task.create(member, "로그인 API 구현", TaskCategory.DEVELOP, TaskStatus.TODO,
                LocalDate.now().plusDays(3));
        task.applyCompetencyClassification(new TaskCompetencyClassification(
                CompetencyCategory.OUTPUT, new BigDecimal("0.8765"), "task-title-anchor-v1"));
        taskRepository.saveAndFlush(task);
        entityManager.clear();

        Task stored = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(stored.getInferredCompetency()).isEqualTo(CompetencyCategory.OUTPUT);
        assertThat(stored.getCompetencyConfidence()).isEqualByComparingTo("0.8765");
        assertThat(stored.getCompetencyClassifierVersion()).isEqualTo("task-title-anchor-v1");
    }
}
