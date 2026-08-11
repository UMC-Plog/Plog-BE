package com.plog.domain.project.event;

import com.plog.domain.integration.event.ExternalCollectionFinishedEvent;
import com.plog.domain.integration.event.ExternalCollectionStartedEvent;
import com.plog.domain.project.service.ProjectDeadlineService;
import com.plog.domain.report.service.InternalActivityCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectCollectionLifecycleListener {

    private final ProjectDeadlineService projectDeadlineService;
    private final InternalActivityCollectionService internalActivityCollectionService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInternalCollectionRequested(InternalActivityCollectionRequestedEvent event) {
        projectDeadlineService.markInternalCollectionRunning(event.projectId());
        try {
            internalActivityCollectionService.collectProject(event.projectId());
            projectDeadlineService.finishInternalCollection(event.projectId(), true);
        } catch (RuntimeException exception) {
            projectDeadlineService.finishInternalCollection(event.projectId(), false);
            log.error("internal_activity_collection_failed projectId={}", event.projectId(), exception);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExternalCollectionFinished(ExternalCollectionFinishedEvent event) {
        projectDeadlineService.completeExternalCollection(event.projectId(), event.status());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExternalCollectionStarted(ExternalCollectionStartedEvent event) {
        projectDeadlineService.markExternalCollectionRunning(event.projectId());
    }
}
