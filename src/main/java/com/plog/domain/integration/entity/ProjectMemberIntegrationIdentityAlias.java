package com.plog.domain.integration.entity;

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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** email/login fallback actor 매핑. 중복 별칭은 저장 단계에서 거절한다. */
@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "project_member_integration_identity_aliases", uniqueConstraints = {
        @UniqueConstraint(name = "uk_integration_identity_alias", columnNames = {
                "project_integration_id", "alias_type", "alias_value"
        })
})
public class ProjectMemberIntegrationIdentityAlias extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_member_integration_identity_alias_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_member_integration_identity_id", nullable = false)
    private ProjectMemberIntegrationIdentity identity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_integration_id", nullable = false)
    private ProjectIntegration projectIntegration;

    @Enumerated(EnumType.STRING)
    @Column(name = "alias_type", nullable = false)
    private IntegrationIdentityAliasType aliasType;

    @Column(name = "alias_value", nullable = false)
    private String aliasValue;
}
