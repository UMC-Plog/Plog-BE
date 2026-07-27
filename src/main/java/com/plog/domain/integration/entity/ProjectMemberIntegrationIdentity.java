package com.plog.domain.integration.entity;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** 프로젝트 멤버와 provider actor를 명시적으로 연결하는 우선 매핑이다. */
@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "project_member_integration_identities", uniqueConstraints = {
        @UniqueConstraint(name = "uk_member_integration_identity", columnNames = {
                "project_integration_id", "provider_actor_id"
        })
})
public class ProjectMemberIntegrationIdentity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_member_integration_identity_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_integration_id", nullable = false)
    private ProjectIntegration projectIntegration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_member_id", nullable = false)
    private ProjectMember projectMember;

    @Column(name = "provider_actor_id", nullable = false)
    private String providerActorId;

    @Column(name = "provider_login")
    private String providerLogin;

    @Column(name = "provider_email")
    private String providerEmail;
}
