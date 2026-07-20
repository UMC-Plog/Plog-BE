package com.plog.infrastructure.s3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileDeletionEventListener {
    private final FileStorageService fileStorageService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deleteFiles(FileDeletionEvent event) {
        for (String fileKey : event.fileKeys()) {
            retry(fileKey, "delete", () -> fileStorageService.delete(fileKey));
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void promoteFiles(FilePromotionEvent event) {
        for (String fileKey : event.fileKeys()) {
            retry(fileKey, "promote", () -> fileStorageService.markPermanent(fileKey));
        }
    }

    private void retry(String fileKey, String operation, Runnable action) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                action.run();
                return;
            } catch (RuntimeException exception) {
                if (attempt == 3) {
                    log.error("s3_file_operation_failed operation={} fileKey={} attempts={}",
                            operation, fileKey, attempt, exception);
                    return;
                }
                try {
                    Thread.sleep(200L * (1L << (attempt - 1)));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    log.error("s3_file_operation_failed operation={} fileKey={} attempts={} reason=interrupted",
                            operation, fileKey, attempt, interrupted);
                    return;
                }
            }
        }
    }
}
