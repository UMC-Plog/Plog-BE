package com.plog.domain.integration.repository;

import com.plog.domain.integration.entity.IntegrationAuthorizationState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationAuthorizationStateRepository extends JpaRepository<IntegrationAuthorizationState, Long> {

    Optional<IntegrationAuthorizationState> findByStateHash(String stateHash);
}
