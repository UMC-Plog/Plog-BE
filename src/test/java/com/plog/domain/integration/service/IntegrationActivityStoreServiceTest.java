package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.plog.domain.integration.entity.IntegrationActivityType;
import com.plog.domain.integration.entity.IntegrationCredentialType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.IntegrationActivityRepository;
import com.plog.domain.project.entity.ProjectMember;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntegrationActivityStoreServiceTest {

    @Mock
    private IntegrationActivityRepository integrationActivityRepository;

    @Mock
    private IntegrationActorMappingService integrationActorMappingService;

    @InjectMocks
    private IntegrationActivityStoreService integrationActivityStoreService;

    @Test
    void storesAnEventWithAnAtomicInsert() {
        IntegrationResource resource = resource();
        ProjectMember member = ProjectMember.builder().id(20L).build();
        given(integrationActorMappingService.resolve(any(), eq("actor-1"), eq("vana"), eq("vana@plog.test")))
                .willReturn(member);

        integrationActivityStoreService.store(
                resource,
                IntegrationActivityType.GITHUB_COMMIT,
                "commit:abc123",
                "actor-1",
                "vana",
                "vana@plog.test",
                Instant.parse("2026-07-26T00:00:00Z"),
                "https://github.com/UMC-Plog/Plog-BE/commit/abc123",
                "{\"sha\":\"abc123\"}"
        );

        verify(integrationActivityRepository).insertIfAbsent(
                10L,
                20L,
                IntegrationActivityType.GITHUB_COMMIT.name(),
                "commit:abc123",
                "actor-1",
                "vana",
                "vana@plog.test",
                Instant.parse("2026-07-26T00:00:00Z"),
                "https://github.com/UMC-Plog/Plog-BE/commit/abc123",
                "{\"sha\":\"abc123\"}"
        );
    }

    @Test
    void ignoresAnEmptyProviderEventKey() {
        integrationActivityStoreService.store(
                resource(), IntegrationActivityType.GITHUB_COMMIT, " ", null, null, null, null, null, null
        );

        verify(integrationActivityRepository, never()).insertIfAbsent(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void backfillsActorSnapshotThroughRepository() {
        given(integrationActivityRepository.backfillActorSnapshotByProviderId(
                1L, "actor-1", "vana", "vana@plog.test"
        )).willReturn(3);

        int updated = integrationActivityStoreService.backfillActorDisplayInfo(
                1L, "actor-1", "vana", "vana@plog.test"
        );

        assertThat(updated).isEqualTo(3);
        verify(integrationActivityRepository).backfillActorSnapshotByProviderId(
                1L, "actor-1", "vana", "vana@plog.test"
        );
    }

    @Test
    void skipsActorSnapshotBackfillWithoutUsableLookupOrSnapshot() {
        assertThat(integrationActivityStoreService.backfillActorDisplayInfo(1L, " ", "vana", "vana@plog.test"))
                .isZero();
        assertThat(integrationActivityStoreService.backfillActorDisplayInfo(1L, "actor-1", " ", null))
                .isZero();
        assertThat(integrationActivityStoreService.backfillActorDisplayInfo(null, "actor-1", "vana", "vana@plog.test"))
                .isZero();

        verify(integrationActivityRepository, never()).backfillActorSnapshotByProviderId(
                anyLong(), any(), any(), any()
        );
        verifyNoInteractions(integrationActorMappingService);
    }

    private IntegrationResource resource() {
        return IntegrationResource.builder()
                .id(10L)
                .projectIntegration(ProjectIntegration.builder()
                        .id(1L)
                        .linkType(LinkType.GITHUB)
                        .credentialType(IntegrationCredentialType.APP_INSTALLATION)
                        .externalAccountId("UMC-Plog")
                        .externalAccountName("UMC-Plog")
                        .providerConnectionId("1")
                        .build())
                .build();
    }
}
