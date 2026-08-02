package com.plog.domain.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotionWebhookEventRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private NotionWebhookEventRepository repository;

    @Test
    void ignoresDuplicateEventIdWithAtomicInsert() {
        Instant occurredAt = Instant.parse("2026-08-02T12:00:00Z");
        Instant availableAt = occurredAt.plusSeconds(180);

        int first = insert("event-1", occurredAt, availableAt);
        int duplicate = insert("event-1", occurredAt, availableAt.plusSeconds(10));

        assertThat(first).isOne();
        assertThat(duplicate).isZero();
        assertThat(repository.count()).isOne();
    }

    @Test
    void debounceDoesNotShortenProviderRetryDelay() {
        Instant occurredAt = Instant.parse("2026-08-02T12:00:00Z");
        Instant retryAt = occurredAt.plusSeconds(600);
        insert("event-1", occurredAt, retryAt);

        repository.postponePendingGroup(
                "workspace-1",
                "page-1",
                List.of(com.plog.domain.integration.entity.NotionWebhookEventStatus.PENDING),
                occurredAt.plusSeconds(180)
        );

        assertThat(repository.findAll().getFirst().getAvailableAt()).isEqualTo(retryAt);
    }

    private int insert(String eventId, Instant occurredAt, Instant availableAt) {
        return repository.insertIfAbsent(
                eventId,
                "subscription-1",
                "workspace-1",
                "integration-1",
                "page.content_updated",
                "page-1",
                "page",
                null,
                null,
                "[]",
                occurredAt,
                "{\"id\":\"" + eventId + "\"}",
                availableAt
        );
    }
}
