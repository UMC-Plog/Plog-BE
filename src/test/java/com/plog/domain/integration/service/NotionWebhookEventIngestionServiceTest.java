package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.integration.config.NotionWebhookProperties;
import com.plog.domain.integration.repository.NotionWebhookEventRepository;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotionWebhookEventIngestionServiceTest {

    @Mock
    private NotionWebhookSignatureVerifier signatureVerifier;

    @Mock
    private NotionWebhookEventRepository repository;

    private NotionWebhookEventIngestionService service;

    @BeforeEach
    void setUp() {
        service = new NotionWebhookEventIngestionService(
                new ObjectMapper(),
                new NotionWebhookProperties(
                        "verification-token", Duration.ofMinutes(3), Duration.ofMinutes(30),
                        5, 10_000, 10),
                signatureVerifier,
                repository
        );
    }

    @Test
    void acceptsInitialVerificationWithoutSignatureOrQueueWrite() {
        NotionWebhookEventIngestionService.IngestionResult result = service.ingest(
                "{\"verification_token\":\"setup-token\"}", null);

        assertThat(result).isEqualTo(
                NotionWebhookEventIngestionService.IngestionResult.VERIFICATION_ACCEPTED);
        verify(repository, never()).insertIfAbsent(
                anyString(), any(), anyString(), any(), anyString(), anyString(), anyString(),
                any(), any(), anyString(), any(), anyString(), any());
    }

    @Test
    void rejectsEventWithInvalidSignatureBeforeQueueWrite() {
        String body = eventBody();
        given(signatureVerifier.verify("verification-token", body, "invalid")).willReturn(false);

        NotionWebhookEventIngestionService.IngestionResult result = service.ingest(body, "invalid");

        assertThat(result).isEqualTo(
                NotionWebhookEventIngestionService.IngestionResult.INVALID_SIGNATURE);
        verify(repository, never()).insertIfAbsent(
                anyString(), any(), anyString(), any(), anyString(), anyString(), anyString(),
                any(), any(), anyString(), any(), anyString(), any());
    }

    @Test
    void storesVerifiedEventOnceAndPostponesItsDebounceGroup() {
        String body = eventBody();
        given(signatureVerifier.verify("verification-token", body, "signature")).willReturn(true);
        given(repository.insertIfAbsent(
                anyString(), any(), anyString(), any(), anyString(), anyString(), anyString(),
                any(), any(), anyString(), any(), anyString(), any()))
                .willReturn(1);

        NotionWebhookEventIngestionService.IngestionResult result = service.ingest(body, "signature");

        assertThat(result).isEqualTo(
                NotionWebhookEventIngestionService.IngestionResult.EVENT_ACCEPTED);
        verify(repository).postponePendingGroup(
                org.mockito.ArgumentMatchers.eq("workspace-1"),
                org.mockito.ArgumentMatchers.eq("page-1"),
                any(), any());
    }

    @Test
    void duplicateDeliveryDoesNotExtendDebounceWindow() {
        String body = eventBody();
        given(signatureVerifier.verify("verification-token", body, "signature")).willReturn(true);
        given(repository.insertIfAbsent(
                anyString(), any(), anyString(), any(), anyString(), anyString(), anyString(),
                any(), any(), anyString(), any(), anyString(), any()))
                .willReturn(0);

        NotionWebhookEventIngestionService.IngestionResult result = service.ingest(body, "signature");

        assertThat(result).isEqualTo(
                NotionWebhookEventIngestionService.IngestionResult.DUPLICATE_IGNORED);
        verify(repository, never()).postponePendingGroup(anyString(), anyString(), any(), any());
    }

    private String eventBody() {
        return """
                {
                  "id":"event-1",
                  "subscription_id":"subscription-1",
                  "workspace_id":"workspace-1",
                  "integration_id":"integration-1",
                  "type":"page.content_updated",
                  "timestamp":"2026-08-02T10:00:00Z",
                  "entity":{"id":"page-1","type":"page"},
                  "data":{"parent":{"id":"root-page","type":"page"}},
                  "authors":[{"id":"actor-1","type":"person"}]
                }
                """;
    }
}
