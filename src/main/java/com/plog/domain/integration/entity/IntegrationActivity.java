package com.plog.domain.integration.entity;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** provider 활동 원문과 정규화된 actor 후보를 함께 보관한다. */
@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "integration_activities", uniqueConstraints = {
        @UniqueConstraint(name = "uk_integration_activity_provider", columnNames = {
                "integration_resource_id", "provider_event_key"
        })
})
public class IntegrationActivity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "integration_activity_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_resource_id", nullable = false)
    private IntegrationResource integrationResource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_member_id")
    private ProjectMember projectMember;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false)
    private IntegrationActivityType activityType;

    @Column(name = "provider_event_key", nullable = false)
    private String providerEventKey;

    @Column(name = "actor_provider_id")
    private String actorProviderId;

    @Column(name = "actor_login")
    private String actorLogin;

    @Column(name = "actor_email")
    private String actorEmail;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Column(name = "provider_payload", columnDefinition = "TEXT", nullable = false)
    private String providerPayload;
}
