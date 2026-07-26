package com.plog.domain.integration.repository;

import com.plog.domain.integration.entity.IntegrationActivity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationActivityRepository extends JpaRepository<IntegrationActivity, Long> {

    boolean existsByIntegrationResourceIdAndProviderEventKey(Long integrationResourceId, String providerEventKey);

    void deleteAllByIntegrationResourceProjectIntegrationId(Long projectIntegrationId);
}
