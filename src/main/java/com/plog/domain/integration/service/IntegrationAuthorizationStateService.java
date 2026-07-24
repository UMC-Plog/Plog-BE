package com.plog.domain.integration.service;

import com.plog.domain.integration.entity.IntegrationAuthorizationState;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.repository.IntegrationAuthorizationStateRepository;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IntegrationAuthorizationStateService {
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final IntegrationAuthorizationStateRepository authorizationStateRepository;

    @Transactional
    public IssuedState issue(ProjectMember projectMember, LinkType linkType) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(STATE_TTL);
        authorizationStateRepository.save(IntegrationAuthorizationState.builder()
                .project(projectMember.getProject())
                .projectMember(projectMember)
                .linkType(linkType)
                .stateHash(sha256(state))
                .expiresAt(expiresAt)
                .build());
        return new IssuedState(state, expiresAt);
    }

    @Transactional
    public IntegrationAuthorizationState consume(String state, LinkType linkType) {
        if (state == null || state.isBlank()) {
            throw new ApiException(IntegrationErrorCode.AUTHORIZATION_STATE_INVALID);
        }
        try {
            IntegrationAuthorizationState authorizationState = authorizationStateRepository
                    .findByStateHashForUpdate(sha256(state))
                    .orElseThrow(() -> new ApiException(IntegrationErrorCode.AUTHORIZATION_STATE_INVALID));
            Instant now = Instant.now();
            if (authorizationState.getLinkType() != linkType || authorizationState.getConsumedAt() != null) {
                throw new ApiException(IntegrationErrorCode.AUTHORIZATION_STATE_INVALID);
            }
            if (!authorizationState.isUsableAt(now)) {
                throw new ApiException(IntegrationErrorCode.AUTHORIZATION_STATE_EXPIRED);
            }
            authorizationState.consume(now);
            return authorizationStateRepository.saveAndFlush(authorizationState);
        } catch (ObjectOptimisticLockingFailureException | PessimisticLockingFailureException exception) {
            throw new ApiException(IntegrationErrorCode.AUTHORIZATION_STATE_INVALID, exception);
        }
    }

    private String sha256(String state) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(state.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record IssuedState(String value, Instant expiresAt) {}
}
