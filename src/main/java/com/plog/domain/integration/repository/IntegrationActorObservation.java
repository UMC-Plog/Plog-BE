package com.plog.domain.integration.repository;

import java.time.Instant;

public interface IntegrationActorObservation {

    String getActorProviderId();

    String getActorLogin();

    String getActorEmail();

    long getActivityCount();

    Instant getFirstOccurredAt();

    Instant getLastOccurredAt();
}
