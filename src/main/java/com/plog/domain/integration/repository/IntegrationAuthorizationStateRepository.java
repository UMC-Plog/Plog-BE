package com.plog.domain.integration.repository;

import com.plog.domain.integration.entity.IntegrationAuthorizationState;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IntegrationAuthorizationStateRepository extends JpaRepository<IntegrationAuthorizationState, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from IntegrationAuthorizationState state where state.stateHash = :stateHash")
    Optional<IntegrationAuthorizationState> findByStateHashForUpdate(@Param("stateHash") String stateHash);
}
