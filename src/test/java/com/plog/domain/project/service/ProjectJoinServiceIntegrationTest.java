package com.plog.domain.project.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.plog.domain.project.dto.request.ProjectJoinRequest;
import com.plog.domain.project.dto.response.ProjectJoinResponse;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.util.HashUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "plog.invite.encryption-key-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        ProjectJoinService.class,
        InviteTokenService.class,
        InviteTokenGenerator.class,
        InviteTokenCipher.class
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProjectJoinServiceIntegrationTest {

    private static final String INVITE_CODE = "project-join-invite-code";

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
    private ProjectJoinService projectJoinService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            projectMemberRepository.deleteAll();
            projectMemberRepository.flush();
            projectRepository.deleteAll();
            projectRepository.flush();
            userRepository.deleteAll();
            userRepository.flush();
        });
    }

    @Test
    void persistsANewActiveMembership() {
        Fixture fixture = saveFixture("new-member");

        ProjectJoinResponse response = projectJoinService.join(
                fixture.userId(),
                new ProjectJoinRequest(INVITE_CODE)
        );

        assertThat(response.projectId()).isEqualTo(fixture.projectId());
        assertThat(response.role()).isEqualTo(ProjectRole.MEMBER);
        assertThat(response.projectStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(response.memberStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(response.joinedAt()).isNotNull();
        assertThat(projectMemberRepository.count()).isEqualTo(1);
    }

    @Test
    void reactivatesTheExitedMembershipWithoutCreatingAnotherRow() {
        Fixture fixture = saveFixture("rejoined-member");
        Long exitedMemberId = saveExitedMember(fixture);
        LocalDateTime previousUpdatedAt = projectMemberRepository.findById(exitedMemberId)
                .orElseThrow()
                .getUpdatedAt();

        ProjectJoinResponse response = projectJoinService.join(
                fixture.userId(),
                new ProjectJoinRequest(INVITE_CODE)
        );

        assertThat(response.projectMemberId()).isEqualTo(exitedMemberId);
        assertThat(response.role()).isEqualTo(ProjectRole.MEMBER);
        assertThat(response.memberStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(response.joinedAt()).isNotNull();
        assertThat(projectMemberRepository.count()).isEqualTo(1);
        ProjectMember reactivated = projectMemberRepository.findById(exitedMemberId).orElseThrow();
        assertThat(reactivated.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(reactivated.getUpdatedAt()).isAfter(previousUpdatedAt);
        assertThat(response.joinedAt())
                .isCloseTo(reactivated.getUpdatedAt().toInstant(java.time.ZoneOffset.UTC),
                        within(1, ChronoUnit.MICROS));
    }

    @Test
    void allowsOnlyOneOfTwoConcurrentJoinRequests() throws Exception {
        Fixture fixture = saveFixture("concurrent-member");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = List.of(
                    executor.submit(() -> joinAfterSignal(fixture, ready, start)),
                    executor.submit(() -> joinAfterSignal(fixture, ready, start))
            );
            assertThat(ready.await(5, SECONDS)).isTrue();
            start.countDown();

            List<Object> results = List.of(
                    futures.get(0).get(10, SECONDS),
                    futures.get(1).get(10, SECONDS)
            );

            assertThat(results).filteredOn(ProjectJoinResponse.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(ApiException.class::isInstance)
                    .singleElement()
                    .satisfies(result -> assertThat(((ApiException) result).getErrorCode())
                            .isEqualTo(ProjectErrorCode.PROJECT_ALREADY_JOINED));
            assertThat(projectMemberRepository.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void allowsOnlyOneOfTwoConcurrentRejoinRequests() throws Exception {
        Fixture fixture = saveFixture("concurrent-rejoined-member");
        Long exitedMemberId = saveExitedMember(fixture);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = List.of(
                    executor.submit(() -> joinAfterSignal(fixture, ready, start)),
                    executor.submit(() -> joinAfterSignal(fixture, ready, start))
            );
            assertThat(ready.await(5, SECONDS)).isTrue();
            start.countDown();

            List<Object> results = List.of(
                    futures.get(0).get(10, SECONDS),
                    futures.get(1).get(10, SECONDS)
            );

            assertThat(results).filteredOn(ProjectJoinResponse.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(ApiException.class::isInstance)
                    .singleElement()
                    .satisfies(result -> assertThat(((ApiException) result).getErrorCode())
                            .isEqualTo(ProjectErrorCode.PROJECT_ALREADY_JOINED));
            assertThat(projectMemberRepository.count()).isEqualTo(1);
            assertThat(projectMemberRepository.findById(exitedMemberId).orElseThrow().getStatus())
                    .isEqualTo(MemberStatus.ACTIVE);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void stopsWaitingWhenAnExitedMembershipLockIsHeldTooLong() throws Exception {
        Fixture fixture = saveFixture("locked-rejoined-member");
        saveExitedMember(fixture);
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> lockHolder = executor.submit(() -> {
                TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
                transactionTemplate.executeWithoutResult(status -> {
                    projectMemberRepository.findByProjectIdAndUserIdForUpdate(
                            fixture.projectId(),
                            fixture.userId()
                    ).orElseThrow();
                    lockAcquired.countDown();
                    await(releaseLock);
                });
            });
            assertThat(lockAcquired.await(5, SECONDS)).isTrue();

            Future<Object> blockedJoin = executor.submit(() -> {
                try {
                    return projectJoinService.join(
                            fixture.userId(),
                            new ProjectJoinRequest(INVITE_CODE)
                    );
                } catch (RuntimeException exception) {
                    return exception;
                }
            });

            Object result = blockedJoin.get(5, SECONDS);
            assertThat(result).isInstanceOf(RuntimeException.class);
            assertThat(hasAnyPostgresState((Throwable) result, "55P03", "57014")).isTrue();

            releaseLock.countDown();
            lockHolder.get(5, SECONDS);
        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
        }
    }

    private Object joinAfterSignal(Fixture fixture, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            return projectJoinService.join(fixture.userId(), new ProjectJoinRequest(INVITE_CODE));
        } catch (ApiException exception) {
            return exception;
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while holding the membership lock", exception);
        }
    }

    private boolean hasAnyPostgresState(Throwable exception, String... sqlStates) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof PSQLException postgresException) {
                for (String sqlState : sqlStates) {
                    if (sqlState.equals(postgresException.getSQLState())) {
                        return true;
                    }
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    private Fixture saveFixture(String suffix) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            User user = userRepository.save(User.createLocal(
                    suffix + "@plog.test",
                    "encoded-password",
                    "Member",
                    suffix
            ));
            Project project = projectRepository.save(Project.builder()
                    .projectName("Plog API")
                    .inviteTokenHash(HashUtil.sha256Hex(INVITE_CODE))
                    .inviteTokenEncrypted("encrypted-invite")
                    .projectType(ProjectType.DEVELOP)
                    .status(ProjectStatus.IN_PROGRESS)
                    .startDay(LocalDate.now())
                    .endDay(LocalDate.now().plusDays(30))
                    .build());
            return new Fixture(user.getId(), project.getId());
        });
    }

    private Long saveExitedMember(Fixture fixture) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            User user = userRepository.findById(fixture.userId()).orElseThrow();
            Project project = projectRepository.findById(fixture.projectId()).orElseThrow();
            return projectMemberRepository.save(ProjectMember.builder()
                    .user(user)
                    .project(project)
                    .role(ProjectRole.MEMBER)
                    .status(MemberStatus.EXIT)
                    .build()).getId();
        });
    }

    private record Fixture(Long userId, Long projectId) {
    }
}
