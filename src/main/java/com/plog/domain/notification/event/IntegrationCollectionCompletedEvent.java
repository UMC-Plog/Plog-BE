package com.plog.domain.notification.event;

public record IntegrationCollectionCompletedEvent(
        Long projectId,
        Long collectionJobId,
        Long requestedByProjectMemberId
) {
}
