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

/**
 * 프로젝트가 수집하기로 선택한 provider 리소스다.
 *
 * <p>Notion data source는 하나의 리소스로 저장하고, 그 하위 페이지는 수집 시점의 활동 원문으로 보관한다.</p>
 */
@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "integration_resources", uniqueConstraints = {
        @UniqueConstraint(name = "uk_integration_resource_provider", columnNames = {
                "project_integration_id", "provider_resource_id"
        })
})
public class IntegrationResource extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "integration_resource_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_integration_id", nullable = false)
    private ProjectIntegration projectIntegration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_by_project_member_id", nullable = false)
    private ProjectMember selectedByProjectMember;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    private IntegrationResourceType resourceType;

    @Column(name = "provider_resource_id", nullable = false)
    private String providerResourceId;

    @Column(name = "resource_name", nullable = false)
    private String resourceName;

    @Column(name = "resource_url", length = 2048)
    private String resourceUrl;

    @Column(name = "provider_metadata", columnDefinition = "TEXT")
    private String providerMetadata;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_status", nullable = false)
    private IntegrationResourceStatus resourceStatus;

    @Column(name = "last_collected_at")
    private Instant lastCollectedAt;

    /** provider가 마지막으로 수정되었다고 알려준 시각. 등록 대상 식별과 수집 기준점에 사용한다. */
    @Column(name = "last_modified_at")
    private Instant lastModifiedAt;

    public void markCollected(Instant collectedAt) {
        this.lastCollectedAt = collectedAt;
        this.resourceStatus = IntegrationResourceStatus.ACTIVE;
    }

    public void activate() {
        this.resourceStatus = IntegrationResourceStatus.ACTIVE;
    }

    public void reactivate(
            ProjectMember selectedByProjectMember,
            String resourceName,
            String resourceUrl,
            String providerMetadata,
            Instant lastModifiedAt
    ) {
        this.selectedByProjectMember = selectedByProjectMember;
        this.resourceName = resourceName;
        this.resourceUrl = resourceUrl;
        this.providerMetadata = providerMetadata;
        this.lastModifiedAt = lastModifiedAt;
        this.resourceStatus = IntegrationResourceStatus.ACTIVE;
    }

    public void updateProviderMetadata(
            ProjectMember selectedByProjectMember,
            String resourceName,
            String resourceUrl,
            String providerMetadata,
            Instant lastModifiedAt
    ) {
        this.selectedByProjectMember = selectedByProjectMember;
        this.resourceName = resourceName;
        this.resourceUrl = resourceUrl;
        this.providerMetadata = providerMetadata;
        this.lastModifiedAt = lastModifiedAt;
        this.resourceStatus = IntegrationResourceStatus.ACTIVE;
    }

    public void requireReauthorization() {
        this.resourceStatus = IntegrationResourceStatus.REAUTH_REQUIRED;
    }

    public void disable() {
        this.resourceStatus = IntegrationResourceStatus.DISABLED;
    }
}
