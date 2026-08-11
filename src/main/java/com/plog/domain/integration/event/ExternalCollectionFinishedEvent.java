package com.plog.domain.integration.event;

import com.plog.domain.integration.entity.IntegrationCollectionJobStatus;

public record ExternalCollectionFinishedEvent(
        Long projectId,
        Long jobId,
        IntegrationCollectionJobStatus status
) {
}
