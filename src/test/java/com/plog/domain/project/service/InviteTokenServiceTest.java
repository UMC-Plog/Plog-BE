package com.plog.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.plog.domain.project.entity.Project;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.util.HashUtil;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class InviteTokenServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private InviteTokenGenerator inviteTokenGenerator;

    @Mock
    private PlatformTransactionManager transactionManager;

    private InviteTokenCipher inviteTokenCipher;
    private InviteTokenService inviteTokenService;

    @BeforeEach
    void setUp() {
        String encryptionKey = Base64.getEncoder().encodeToString(new byte[32]);
        inviteTokenCipher = new InviteTokenCipher(encryptionKey);
        inviteTokenService = new InviteTokenService(
                projectRepository,
                inviteTokenGenerator,
                inviteTokenCipher,
                transactionManager
        );
    }

    @Test
    void persistsAHashedAndEncryptedTokenInANewTransaction() {
        enableTransactions();
        String rawToken = "url-safe-raw-token";
        given(inviteTokenGenerator.generate()).willReturn(rawToken);

        InviteTokenService.IssuedResult<String> result =
                inviteTokenService.issueAndPersist(token -> "persisted:" + token.hash());

        assertThat(result.token().rawValue()).isEqualTo(rawToken);
        assertThat(result.token().hash()).isEqualTo(HashUtil.sha256Hex(rawToken));
        assertThat(result.token().encryptedValue()).isNotEqualTo(rawToken);
        assertThat(inviteTokenCipher.decrypt(result.token().encryptedValue())).isEqualTo(rawToken);
        assertThat(result.value()).isEqualTo("persisted:" + HashUtil.sha256Hex(rawToken));
        assertThat(result.toString())
                .doesNotContain(rawToken, result.token().hash(), result.token().encryptedValue())
                .contains("REDACTED");

        ArgumentCaptor<TransactionDefinition> definitionCaptor =
                ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definitionCaptor.capture());
        assertThat(definitionCaptor.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    void reservesTheCandidateInPostgresBeforeCheckingAndPersistingIt() {
        enableTransactions();
        String rawToken = "candidate-token";
        String tokenHash = HashUtil.sha256Hex(rawToken);
        given(inviteTokenGenerator.generate()).willReturn(rawToken);
        given(projectRepository.existsByInviteTokenHash(tokenHash)).willReturn(false);
        AtomicInteger persistenceCalls = new AtomicInteger();

        inviteTokenService.issueAndPersist(token -> {
            persistenceCalls.incrementAndGet();
            return token.rawValue();
        });

        InOrder order = inOrder(projectRepository);
        order.verify(projectRepository).acquireInviteTokenCandidateLock(anyLong());
        order.verify(projectRepository).existsByInviteTokenHash(tokenHash);
        assertThat(persistenceCalls).hasValue(1);
    }

    @Test
    void retriesWithoutPersistingWhenAReservedCandidateAlreadyExists() {
        enableTransactions();
        String collidingToken = "already-reserved-token";
        String uniqueToken = "new-candidate-token";
        String collidingHash = HashUtil.sha256Hex(collidingToken);
        String uniqueHash = HashUtil.sha256Hex(uniqueToken);
        given(inviteTokenGenerator.generate()).willReturn(collidingToken, uniqueToken);
        given(projectRepository.existsByInviteTokenHash(collidingHash)).willReturn(true);
        given(projectRepository.existsByInviteTokenHash(uniqueHash)).willReturn(false);
        AtomicInteger persistenceCalls = new AtomicInteger();

        InviteTokenService.IssuedResult<String> result = inviteTokenService.issueAndPersist(token -> {
            persistenceCalls.incrementAndGet();
            return token.rawValue();
        });

        assertThat(result.token().rawValue()).isEqualTo(uniqueToken);
        assertThat(persistenceCalls).hasValue(1);
        verify(inviteTokenGenerator, times(2)).generate();
        verify(projectRepository, times(2)).acquireInviteTokenCandidateLock(anyLong());
    }

    @Test
    void doesNotExposeTokenMaterialThroughStringRepresentation() {
        String rawToken = "secret-raw-token";
        String tokenHash = "secret-token-hash";
        String encryptedToken = "secret-encrypted-token";
        InviteTokenService.IssuedToken issuedToken =
                new InviteTokenService.IssuedToken(rawToken, tokenHash, encryptedToken);

        assertThat(issuedToken.toString())
                .doesNotContain(rawToken, tokenHash, encryptedToken)
                .contains("REDACTED");
    }

    @Test
    void retriesInANewTransactionWhenTheInviteTokenConstraintCollides() {
        enableTransactions();
        String collidingToken = "colliding-token";
        String uniqueToken = "unique-token";
        given(inviteTokenGenerator.generate()).willReturn(collidingToken, uniqueToken);
        doThrow(uniqueViolation("duplicate secret-token-hash"))
                .doNothing()
                .when(transactionManager).commit(any(TransactionStatus.class));

        InviteTokenService.IssuedResult<String> result =
                inviteTokenService.issueAndPersist(InviteTokenService.IssuedToken::rawValue);

        assertThat(result.token().rawValue()).isEqualTo(uniqueToken);
        assertThat(result.value()).isEqualTo(uniqueToken);
        verify(inviteTokenGenerator, times(2)).generate();
        verify(transactionManager, times(2)).getTransaction(any(TransactionDefinition.class));
    }

    @Test
    void retriesWhenPostgresReportsTheInviteTokenConstraintDirectly() {
        enableTransactions();
        String collidingToken = "postgres-colliding-token";
        String uniqueToken = "postgres-unique-token";
        given(inviteTokenGenerator.generate()).willReturn(collidingToken, uniqueToken);
        doThrow(postgresUniqueViolation("uk_project_invite_token", "duplicate key"))
                .doNothing()
                .when(transactionManager).commit(any(TransactionStatus.class));

        InviteTokenService.IssuedResult<String> result =
                inviteTokenService.issueAndPersist(InviteTokenService.IssuedToken::rawValue);

        assertThat(result.token().rawValue()).isEqualTo(uniqueToken);
        verify(inviteTokenGenerator, times(2)).generate();
        verify(transactionManager, times(2)).getTransaction(any(TransactionDefinition.class));
    }

    @Test
    void doesNotRetryAnotherPostgresUniqueConstraint() {
        enableTransactions();
        given(inviteTokenGenerator.generate()).willReturn("candidate-token");
        DataIntegrityViolationException unrelated =
                postgresUniqueViolation("uk_other_constraint", "unrelated duplicate key");
        doThrow(unrelated).when(transactionManager).commit(any(TransactionStatus.class));

        assertThatThrownBy(() -> inviteTokenService.issueAndPersist(token -> token))
                .isSameAs(unrelated);
        verify(inviteTokenGenerator).generate();
    }

    @Test
    void failsWithoutLeakingTheDatabaseCauseAfterFiveInviteTokenCollisions() {
        enableTransactions();
        String collidingToken = "always-colliding-token";
        given(inviteTokenGenerator.generate()).willReturn(collidingToken);
        doThrow(uniqueViolation("duplicate secret-token-hash"))
                .when(transactionManager).commit(any(TransactionStatus.class));

        assertThatThrownBy(() -> inviteTokenService.issueAndPersist(token -> token))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ProjectErrorCode.INVITE_TOKEN_GENERATION_ERROR);
                    assertThat(exception.getCause()).isNull();
                    assertThat(exception.getMessage()).doesNotContain(collidingToken, "secret-token-hash");
                });
        verify(inviteTokenGenerator, times(5)).generate();
        verify(transactionManager, times(5)).getTransaction(any(TransactionDefinition.class));
    }

    @Test
    void doesNotRetryAnUnrelatedDatabaseConstraint() {
        enableTransactions();
        given(inviteTokenGenerator.generate()).willReturn("candidate-token");
        DataIntegrityViolationException unrelated = constraintViolation("uk_other_constraint", "unrelated");
        doThrow(unrelated).when(transactionManager).commit(any(TransactionStatus.class));

        assertThatThrownBy(() -> inviteTokenService.issueAndPersist(token -> token))
                .isSameAs(unrelated);
        verify(inviteTokenGenerator).generate();
    }

    @Test
    void findsAProjectByHashingTheRawToken() {
        String rawToken = "raw-invite-token";
        Project project = Project.builder()
                .id(10L)
                .inviteTokenHash("stored-hash")
                .inviteTokenEncrypted("stored-encrypted-token")
                .build();
        given(projectRepository.findByInviteTokenHash(HashUtil.sha256Hex(rawToken)))
                .willReturn(Optional.of(project));

        Optional<Project> foundProject = inviteTokenService.findProjectByRawToken(rawToken);

        assertThat(foundProject).containsSame(project);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-registered-token", "%malformed-token%", "토큰"})
    void safelyHashesUnknownNonBlankTokensBeforeLookup(String rawToken) {
        given(projectRepository.findByInviteTokenHash(HashUtil.sha256Hex(rawToken)))
                .willReturn(Optional.empty());

        assertThat(inviteTokenService.findProjectByRawToken(rawToken)).isEmpty();

        verify(projectRepository).findByInviteTokenHash(HashUtil.sha256Hex(rawToken));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void doesNotQueryTheRepositoryForABlankRawToken(String rawToken) {
        assertThat(inviteTokenService.findProjectByRawToken(rawToken)).isEmpty();
        verifyNoInteractions(projectRepository);
    }

    @Test
    void locksTheProjectAndRotatesBothStoredTokenRepresentationsTogether() {
        enableTransactions();
        Project project = Project.builder()
                .id(10L)
                .inviteTokenHash("old-hash")
                .inviteTokenEncrypted("old-encrypted-value")
                .build();
        String newRawToken = "new-raw-token";
        given(inviteTokenGenerator.generate()).willReturn(newRawToken);
        given(projectRepository.findByIdForUpdate(10L)).willReturn(Optional.of(project));

        InviteTokenService.IssuedToken issuedToken = inviteTokenService.rotate(10L);

        assertThat(issuedToken.rawValue()).isEqualTo(newRawToken);
        assertThat(project.getInviteTokenHash()).isEqualTo(issuedToken.hash());
        assertThat(project.getInviteTokenEncrypted()).isEqualTo(issuedToken.encryptedValue());
        assertThat(inviteTokenCipher.decrypt(project.getInviteTokenEncrypted())).isEqualTo(newRawToken);
        verify(projectRepository).findByIdForUpdate(10L);
    }

    @Test
    void rejectsRotationWhenEveryCandidateMatchesTheCurrentToken() {
        enableTransactions();
        String currentRawToken = "current-raw-token";
        Project project = Project.builder()
                .id(10L)
                .inviteTokenHash(HashUtil.sha256Hex(currentRawToken))
                .inviteTokenEncrypted(inviteTokenCipher.encrypt(currentRawToken))
                .build();
        given(inviteTokenGenerator.generate()).willReturn(currentRawToken);
        given(projectRepository.findByIdForUpdate(10L)).willReturn(Optional.of(project));

        assertThatThrownBy(() -> inviteTokenService.rotate(10L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ProjectErrorCode.INVITE_TOKEN_GENERATION_ERROR));

        verify(inviteTokenGenerator, times(5)).generate();
    }

    private void enableTransactions() {
        given(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .willAnswer(invocation -> new SimpleTransactionStatus());
    }

    private DataIntegrityViolationException uniqueViolation(String message) {
        return constraintViolation("uk_project_invite_token", message);
    }

    private DataIntegrityViolationException constraintViolation(String constraintName, String message) {
        ConstraintViolationException hibernateException = mock(ConstraintViolationException.class);
        given(hibernateException.getConstraintName()).willReturn(constraintName);
        return new DataIntegrityViolationException(message, hibernateException);
    }

    private DataIntegrityViolationException postgresUniqueViolation(String constraintName, String message) {
        String separator = Character.toString(0);
        String serverMessage = String.join(
                separator,
                "SERROR",
                "C23505",
                "M" + message,
                "n" + constraintName
        ) + separator + separator;
        PSQLException postgresException = new PSQLException(new ServerErrorMessage(serverMessage));
        return new DataIntegrityViolationException(message, postgresException);
    }
}
