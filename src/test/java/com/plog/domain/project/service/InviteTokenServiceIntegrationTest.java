package com.plog.domain.project.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.util.HashUtil;
import java.time.LocalDate;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
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

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InviteTokenServiceIntegrationTest {

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
    private ProjectRepository projectRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private InviteTokenCipher inviteTokenCipher;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        String encryptionKey = Base64.getEncoder().encodeToString(new byte[32]);
        inviteTokenCipher = new InviteTokenCipher(encryptionKey);
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            projectRepository.deleteAll();
            projectRepository.flush();
        });
    }

    @Test
    void retriesAnExistingCandidateInAFreshTransaction() {
        String collidingRawToken = "already-persisted-token";
        String uniqueRawToken = "new-unique-token";
        saveProject("existing", collidingRawToken);
        InviteTokenGenerator generator = mock(InviteTokenGenerator.class);
        given(generator.generate()).willReturn(collidingRawToken, uniqueRawToken);
        InviteTokenService service = new InviteTokenService(
                projectRepository,
                generator,
                inviteTokenCipher,
                transactionManager
        );

        InviteTokenService.IssuedResult<Project> result = service.issueAndPersist(token ->
                projectRepository.save(newProject("created", token))
        );

        assertThat(result.token().rawValue()).isEqualTo(uniqueRawToken);
        assertThat(projectRepository.count()).isEqualTo(2);
        assertThat(projectRepository.findByInviteTokenHash(HashUtil.sha256Hex(uniqueRawToken)))
                .isPresent();
        verify(generator, times(2)).generate();
    }

    @Test
    void rollsBackEveryCollisionAndReturnsARedactedDomainError() {
        String collidingRawToken = "always-colliding-token";
        saveProject("existing", collidingRawToken);
        InviteTokenGenerator generator = mock(InviteTokenGenerator.class);
        given(generator.generate()).willReturn(collidingRawToken);
        InviteTokenService service = new InviteTokenService(
                projectRepository,
                generator,
                inviteTokenCipher,
                transactionManager
        );

        assertThatThrownBy(() -> service.issueAndPersist(token ->
                projectRepository.save(newProject("collision", token))))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ProjectErrorCode.INVITE_TOKEN_GENERATION_ERROR);
                    assertThat(exception.getCause()).isNull();
                    assertThat(exception.getMessage()).doesNotContain(collidingRawToken);
                });

        assertThat(projectRepository.count()).isEqualTo(1);
        verify(generator, times(5)).generate();
    }

    @Test
    void keepsBothPreviousColumnsWhenEveryRotationCandidateCollides() {
        String occupiedRawToken = "occupied-token";
        String previousRawToken = "previous-token";
        saveProject("occupied", occupiedRawToken);
        Project target = saveProject("target", previousRawToken);
        String previousHash = target.getInviteTokenHash();
        String previousEncrypted = target.getInviteTokenEncrypted();
        InviteTokenGenerator generator = mock(InviteTokenGenerator.class);
        given(generator.generate()).willReturn(occupiedRawToken);
        InviteTokenService service = new InviteTokenService(
                projectRepository,
                generator,
                inviteTokenCipher,
                transactionManager
        );

        assertThatThrownBy(() -> service.rotate(target.getId()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ProjectErrorCode.INVITE_TOKEN_GENERATION_ERROR));

        Project reloaded = projectRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getInviteTokenHash()).isEqualTo(previousHash);
        assertThat(reloaded.getInviteTokenEncrypted()).isEqualTo(previousEncrypted);
        assertThat(inviteTokenCipher.decrypt(reloaded.getInviteTokenEncrypted()))
                .isEqualTo(previousRawToken);
    }

    @Test
    void retriesWhenARotationCandidateMatchesTheCurrentToken() {
        String previousRawToken = "previous-token";
        String newRawToken = "new-token";
        Project target = saveProject("target", previousRawToken);
        InviteTokenGenerator generator = mock(InviteTokenGenerator.class);
        given(generator.generate()).willReturn(previousRawToken, newRawToken);
        InviteTokenService service = new InviteTokenService(
                projectRepository,
                generator,
                inviteTokenCipher,
                transactionManager
        );

        InviteTokenService.IssuedToken issuedToken = service.rotate(target.getId());

        assertThat(issuedToken.rawValue()).isEqualTo(newRawToken);
        assertThat(projectRepository.findByInviteTokenHash(HashUtil.sha256Hex(previousRawToken)))
                .isEmpty();
        Project reloaded = projectRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getInviteTokenHash()).isEqualTo(HashUtil.sha256Hex(newRawToken));
        assertThat(inviteTokenCipher.decrypt(reloaded.getInviteTokenEncrypted()))
                .isEqualTo(newRawToken);
        verify(generator, times(2)).generate();
    }

    @Test
    void preservesTheCurrentTokenWhenEveryRotationCandidateMatchesIt() {
        String previousRawToken = "unchanged-token";
        Project target = saveProject("target", previousRawToken);
        String previousHash = target.getInviteTokenHash();
        String previousEncrypted = target.getInviteTokenEncrypted();
        InviteTokenGenerator generator = mock(InviteTokenGenerator.class);
        given(generator.generate()).willReturn(previousRawToken);
        InviteTokenService service = new InviteTokenService(
                projectRepository,
                generator,
                inviteTokenCipher,
                transactionManager
        );

        assertThatThrownBy(() -> service.rotate(target.getId()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ProjectErrorCode.INVITE_TOKEN_GENERATION_ERROR));

        Project reloaded = projectRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getInviteTokenHash()).isEqualTo(previousHash);
        assertThat(reloaded.getInviteTokenEncrypted()).isEqualTo(previousEncrypted);
        verify(generator, times(5)).generate();
    }

    @Test
    void serializesConcurrentRotationLocksForTheSameProject() throws Exception {
        Project project = saveProject("locked", "initial-token");
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                projectRepository.findByIdForUpdate(project.getId()).orElseThrow();
                firstLockAcquired.countDown();
                await(releaseFirstLock);
            }));
            assertThat(firstLockAcquired.await(5, SECONDS)).isTrue();

            Future<Throwable> second = executor.submit(() -> {
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        jdbcTemplate.execute("set local lock_timeout = '300ms'");
                        projectRepository.findByIdForUpdate(project.getId()).orElseThrow();
                    });
                    return null;
                } catch (Throwable exception) {
                    return exception;
                }
            });
            Throwable lockFailure = second.get(5, SECONDS);
            assertThat(lockFailure).isNotNull();
            assertThat(allMessages(lockFailure)).containsIgnoringCase("lock timeout");

            releaseFirstLock.countDown();
            first.get(5, SECONDS);
            Project lockedAfterRelease = transactionTemplate.execute(status ->
                    projectRepository.findByIdForUpdate(project.getId()).orElseThrow());
            assertThat(lockedAfterRelease.getId()).isEqualTo(project.getId());
        } finally {
            releaseFirstLock.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void preservesTheRotatedTokenWhenAStaleSettingsWriteCommitsLater() throws Exception {
        String previousRawToken = "stale-settings-token";
        String newRawToken = "token-after-rotation";
        Project project = saveProject("before-settings", previousRawToken);
        InviteTokenGenerator generator = mock(InviteTokenGenerator.class);
        given(generator.generate()).willReturn(newRawToken);
        InviteTokenService service = new InviteTokenService(
                projectRepository,
                generator,
                inviteTokenCipher,
                transactionManager
        );
        CountDownLatch staleProjectLoaded = new CountDownLatch(1);
        CountDownLatch allowSettingsCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> settingsWrite = executor.submit(() ->
                    transactionTemplate.executeWithoutResult(status -> {
                        Project staleProject = projectRepository.findById(project.getId()).orElseThrow();
                        staleProjectLoaded.countDown();
                        await(allowSettingsCommit);
                        staleProject.updateSettings("after-settings", null, null);
                        projectRepository.saveAndFlush(staleProject);
                    }));
            assertThat(staleProjectLoaded.await(5, SECONDS)).isTrue();

            service.rotate(project.getId());
            allowSettingsCommit.countDown();
            settingsWrite.get(5, SECONDS);

            Project reloaded = projectRepository.findById(project.getId()).orElseThrow();
            assertThat(reloaded.getProjectName()).isEqualTo("after-settings");
            assertThat(reloaded.getInviteTokenHash()).isEqualTo(HashUtil.sha256Hex(newRawToken));
            assertThat(inviteTokenCipher.decrypt(reloaded.getInviteTokenEncrypted()))
                    .isEqualTo(newRawToken);
            assertThat(projectRepository.findByInviteTokenHash(HashUtil.sha256Hex(previousRawToken)))
                    .isEmpty();
        } finally {
            allowSettingsCommit.countDown();
            executor.shutdownNow();
        }
    }

    private Project saveProject(String projectName, String rawToken) {
        return transactionTemplate.execute(status -> projectRepository.save(newProject(
                projectName,
                new InviteTokenService.IssuedToken(
                        rawToken,
                        HashUtil.sha256Hex(rawToken),
                        inviteTokenCipher.encrypt(rawToken)
                )
        )));
    }

    private Project newProject(String projectName, InviteTokenService.IssuedToken token) {
        LocalDate today = LocalDate.now();
        return Project.builder()
                .projectName(projectName)
                .inviteTokenHash(token.hash())
                .inviteTokenEncrypted(token.encryptedValue())
                .projectType(ProjectType.DEVELOP)
                .status(ProjectStatus.IN_PROGRESS)
                .startDay(today)
                .endDay(today.plusDays(30))
                .build();
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, SECONDS)) {
                throw new AssertionError("Timed out while waiting for concurrent lock scenario");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for concurrent lock scenario", exception);
        }
    }

    private String allMessages(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        return messages.toString();
    }

}
