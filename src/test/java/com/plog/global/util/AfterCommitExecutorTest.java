package com.plog.global.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AfterCommitExecutorTest {

    private final AfterCommitExecutor afterCommitExecutor = new AfterCommitExecutor();

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void executesImmediatelyWithoutTransaction() {
        AtomicInteger executions = new AtomicInteger();

        afterCommitExecutor.execute(executions::incrementAndGet);

        assertThat(executions).hasValue(1);
    }

    @Test
    void executesOnlyAfterCommitWhenTransactionIsActive() {
        AtomicInteger executions = new AtomicInteger();
        initializeTransactionSynchronization();

        afterCommitExecutor.execute(executions::incrementAndGet);

        assertThat(executions).hasValue(0);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        assertThat(executions).hasValue(1);
    }

    @Test
    void rejectsActiveTransactionWithoutSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatIllegalStateException()
                .isThrownBy(() -> afterCommitExecutor.execute(() -> { }))
                .withMessage("활성 트랜잭션에 synchronization이 등록되지 않았습니다.");
    }

    @Test
    void isolatesFailuresBetweenAfterCommitActions() {
        AtomicInteger successfulExecutions = new AtomicInteger();
        initializeTransactionSynchronization();
        afterCommitExecutor.execute(() -> {
            throw new IllegalStateException("failure");
        });
        afterCommitExecutor.execute(successfulExecutions::incrementAndGet);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        assertThat(successfulExecutions).hasValue(1);
    }

    private void initializeTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }
}
