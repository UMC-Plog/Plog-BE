package com.plog.domain.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.integration.config.IntegrationCollectionProperties;
import com.plog.domain.integration.entity.IntegrationCollectionJob;
import com.plog.domain.integration.entity.IntegrationCollectionJobStatus;
import com.plog.domain.integration.service.IntegrationCollectionJobService;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import({
        IntegrationCollectionJobService.class,
        IntegrationCollectionJobRepositoryIntegrationTest.TestProperties.class
})
class IntegrationCollectionJobRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class TestProperties {
        @Bean
        IntegrationCollectionProperties integrationCollectionProperties() {
            return new IntegrationCollectionProperties(
                    5_000L, 5, Duration.ofMinutes(30), 5, 25, Duration.ofHours(1), 0L, 100);
        }
    }

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private IntegrationCollectionJobRepository integrationCollectionJobRepository;

    @Autowired
    private IntegrationCollectionJobService jobService;

    /**
     * claimNext는 프로젝트를 가리지 않고 큐 전체에서 선점한다(워커가 그렇게 동작해야 한다).
     * 테스트가 활성 잡을 남기면 다음 테스트가 그것을 집어가므로 매번 큐를 비운다.
     */
    @BeforeEach
    void clearQueue() {
        integrationCollectionJobRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("활성 잡이 있으면 enqueue가 같은 잡을 돌려준다")
    void enqueueIsIdempotentWhileJobIsActive() {
        Long projectId = savedProjectId();

        IntegrationCollectionJob first = jobService.enqueue(projectId, null);
        IntegrationCollectionJob second = jobService.enqueue(projectId, null);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(integrationCollectionJobRepository.count()).isOne();
    }

    @Test
    @DisplayName("종료된 잡만 있으면 enqueue가 새 잡을 만든다")
    void enqueueCreatesNewJobAfterTerminalState() {
        Long projectId = savedProjectId();
        IntegrationCollectionJob first = jobService.enqueue(projectId, null);
        IntegrationCollectionJobService.ClaimedJob claimed = jobService.claimNext(Instant.now());
        jobService.succeed(claimed, Instant.now(), 1, 1);

        IntegrationCollectionJob second = jobService.enqueue(projectId, null);

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(second.getStatus()).isEqualTo(IntegrationCollectionJobStatus.PENDING);
    }

    @Test
    @DisplayName("claimNext는 하나만 선점하고 두 번째 호출은 null이다")
    void claimNextTakesSingleJob() {
        Long projectId = savedProjectId();
        jobService.enqueue(projectId, null);

        assertThat(jobService.claimNext(Instant.now())).isNotNull();
        assertThat(jobService.claimNext(Instant.now())).isNull();
    }

    @Test
    @DisplayName("heartbeat가 끊긴 RUNNING 잡을 reclaimStale이 회수한다")
    void reclaimStaleRecoversAbandonedJob() {
        Long projectId = savedProjectId();
        jobService.enqueue(projectId, null);
        jobService.claimNext(Instant.now());
        Instant muchLater = Instant.now().plus(Duration.ofHours(2));

        int reclaimed = jobService.reclaimStale(muchLater);

        assertThat(reclaimed).isOne();
        assertThat(jobService.claimNext(muchLater)).isNotNull();
    }

    @Test
    @DisplayName("findLatest는 가장 최근 잡을 돌려준다")
    void findLatestReturnsMostRecentJob() {
        Long projectId = savedProjectId();
        IntegrationCollectionJob first = jobService.enqueue(projectId, null);
        IntegrationCollectionJobService.ClaimedJob claimed = jobService.claimNext(Instant.now());
        jobService.succeed(claimed, Instant.now(), 1, 1);
        IntegrationCollectionJob second = jobService.enqueue(projectId, null);

        assertThat(jobService.findLatest(projectId))
                .get()
                .extracting(IntegrationCollectionJob::getId)
                .isEqualTo(second.getId());
        assertThat(second.getId()).isNotEqualTo(first.getId());
    }

    private Long savedProjectId() {
        return projectRepository.saveAndFlush(project()).getId();
    }

    private Project project() {
        String suffix = UUID.randomUUID().toString();
        LocalDate today = LocalDate.now();
        return Project.builder()
                .projectName("collection-job-" + suffix)
                .inviteTokenHash("hash-" + suffix)
                .inviteTokenEncrypted("encrypted-" + suffix)
                .projectType(ProjectType.DEVELOP)
                .status(ProjectStatus.IN_PROGRESS)
                .startDay(today)
                .endDay(today.plusDays(1))
                .build();
    }
}
