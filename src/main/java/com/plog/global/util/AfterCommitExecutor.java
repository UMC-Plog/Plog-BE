package com.plog.global.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
public class AfterCommitExecutor {

    public void execute(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("활성 트랜잭션에 synchronization이 등록되지 않았습니다.");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runSafely(action);
            }
        });
    }

    private void runSafely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            log.error("after_commit_action_failed", exception);
        }
    }
}
