package com.plog.domain.integration.service;

import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.repository.IntegrationResourceRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 외부 API 호출과 분리해 리소스 수집 상태만 짧은 트랜잭션으로 갱신한다. */
@Service
@RequiredArgsConstructor
class IntegrationResourceCollectionStateService {

    private final IntegrationResourceRepository integrationResourceRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCollected(Long resourceId, Instant collectedAt) {
        requireResource(resourceId).markCollected(collectedAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requireReauthorization(Long resourceId) {
        requireResource(resourceId).requireReauthorization();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void disable(Long resourceId) {
        requireResource(resourceId).disable();
    }

    private IntegrationResource requireResource(Long resourceId) {
        return integrationResourceRepository.findById(resourceId)
                .orElseThrow(() -> new DataRetrievalFailureException(
                        "Integration resource disappeared during collection: " + resourceId));
    }
}
