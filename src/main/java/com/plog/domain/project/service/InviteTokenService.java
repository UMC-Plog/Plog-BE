package com.plog.domain.project.service;

import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.util.HashUtil;
import java.util.function.Function;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InviteTokenService {
    private static final int MAX_GENERATION_ATTEMPTS = 5;
    private static final String INVITE_TOKEN_UNIQUE_CONSTRAINT = "uk_project_invite_token";

    private final ProjectRepository projectRepository;
    private final InviteTokenGenerator inviteTokenGenerator;
    private final InviteTokenCipher inviteTokenCipher;
    private final TransactionTemplate issueTransaction;

    public InviteTokenService(
            ProjectRepository projectRepository,
            InviteTokenGenerator inviteTokenGenerator,
            InviteTokenCipher inviteTokenCipher,
            PlatformTransactionManager transactionManager
    ) {
        this.projectRepository = projectRepository;
        this.inviteTokenGenerator = inviteTokenGenerator;
        this.inviteTokenCipher = inviteTokenCipher;
        this.issueTransaction = new TransactionTemplate(transactionManager);
        this.issueTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 토큰과 함께 커밋·롤백되어야 하는 모든 DB 변경을 전달해야 한다. 각 시도는 독립 트랜잭션이므로
     * 같은 업무 단위를 콜백 밖의 트랜잭션과 나누거나, 외부 API 호출처럼 재시도할 수 없는 부수 효과를
     * persistenceOperation 안에서 실행하면 안 된다.
     */
    <T> IssuedResult<T> issueAndPersist(Function<IssuedToken, T> persistenceOperation) {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            IssuedToken issuedToken = generateCandidate();
            try {
                T value = issueTransaction.execute(status -> {
                    reserveCandidate(issuedToken.hash());
                    return persistenceOperation.apply(issuedToken);
                });
                return new IssuedResult<>(issuedToken, value);
            } catch (InviteTokenCollisionException ignored) {
                // 이미 예약됐거나 현재 토큰과 같은 후보는 새 후보로 교체한다.
            } catch (DataIntegrityViolationException exception) {
                if (!isInviteTokenUniqueViolation(exception)) {
                    throw exception;
                }
            }
        }
        throw new ApiException(ProjectErrorCode.INVITE_TOKEN_GENERATION_ERROR);
    }

    private IssuedToken generateCandidate() {
        String rawToken = inviteTokenGenerator.generate();
        return new IssuedToken(
                rawToken,
                HashUtil.sha256Hex(rawToken),
                inviteTokenCipher.encrypt(rawToken)
        );
    }

    private void reserveCandidate(String tokenHash) {
        long lockKey = Long.parseUnsignedLong(tokenHash.substring(0, 16), 16);
        projectRepository.acquireInviteTokenCandidateLock(lockKey);
        if (projectRepository.existsByInviteTokenHash(tokenHash)) {
            throw new InviteTokenCollisionException();
        }
    }

    private boolean isInviteTokenUniqueViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && INVITE_TOKEN_UNIQUE_CONSTRAINT.equalsIgnoreCase(
                    constraintViolation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    static final class IssuedToken {
        private final String rawValue;
        private final String hash;
        private final String encryptedValue;

        IssuedToken(String rawValue, String hash, String encryptedValue) {
            this.rawValue = rawValue;
            this.hash = hash;
            this.encryptedValue = encryptedValue;
        }

        String rawValue() {
            return rawValue;
        }

        String hash() {
            return hash;
        }

        String encryptedValue() {
            return encryptedValue;
        }

        @Override
        public String toString() {
            return "IssuedToken[REDACTED]";
        }
    }

    static final class IssuedResult<T> {
        private final IssuedToken token;
        private final T value;

        IssuedResult(IssuedToken token, T value) {
            this.token = token;
            this.value = value;
        }

        IssuedToken token() {
            return token;
        }

        T value() {
            return value;
        }

        @Override
        public String toString() {
            return "IssuedResult[REDACTED]";
        }
    }

    private static final class InviteTokenCollisionException extends RuntimeException {
    }
}
