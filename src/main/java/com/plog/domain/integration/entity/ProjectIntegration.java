package com.plog.domain.integration.entity;

import com.plog.domain.project.entity.Project;
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
 * 프로젝트가 공동으로 사용하는 provider 연결이다.
 *
 * <p>연결을 시작한 멤버는 감사 용도로만 보관하며, 연결과 연결된 리소스는 프로젝트 전체에 속한다.</p>
 */
@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "project_integrations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_project_integration_provider", columnNames = {"project_id", "link_type"})
})
public class ProjectIntegration extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_integration_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connected_by_project_member_id", nullable = false)
    private ProjectMember connectedByProjectMember;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false)
    private LinkType linkType;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", nullable = false)
    private IntegrationCredentialType credentialType;

    @Column(name = "external_account_id", nullable = false)
    private String externalAccountId;

    @Column(name = "external_account_name", nullable = false)
    private String externalAccountName;

    /** GitHub installation ID 등 provider가 발급한 연결 식별자. */
    @Column(name = "provider_connection_id", nullable = false)
    private String providerConnectionId;

    @Column(name = "access_token_encrypted", length = 2048)
    private String accessTokenEncrypted;

    @Column(name = "refresh_token_encrypted", length = 2048)
    private String refreshTokenEncrypted;

    @Column(name = "access_token_expires_at")
    private Instant accessTokenExpiresAt;

    @Getter(AccessLevel.NONE)
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status")
    private IntegrationConnectionStatus connectionStatus = IntegrationConnectionStatus.ACTIVE;

    /** 기존 행의 null 상태는 연결 상태 컬럼 도입 전 생성된 ACTIVE 연결로 해석한다. */
    public IntegrationConnectionStatus getConnectionStatus() {
        return connectionStatus == null ? IntegrationConnectionStatus.ACTIVE : connectionStatus;
    }

    public boolean isConnected() {
        return getConnectionStatus() == IntegrationConnectionStatus.ACTIVE
                && providerConnectionId != null
                && !providerConnectionId.isBlank();
    }

    public boolean canDisconnect() {
        return getConnectionStatus() == IntegrationConnectionStatus.ACTIVE
                || getConnectionStatus() == IntegrationConnectionStatus.REAUTH_REQUIRED;
    }

    public void updateConnection(
            ProjectMember connectedByProjectMember,
            IntegrationCredentialType credentialType,
            String externalAccountId,
            String externalAccountName,
            String providerConnectionId,
            String accessTokenEncrypted,
            String refreshTokenEncrypted,
            Instant accessTokenExpiresAt
    ) {
        this.connectedByProjectMember = connectedByProjectMember;
        this.credentialType = credentialType;
        this.externalAccountId = externalAccountId;
        this.externalAccountName = externalAccountName;
        this.providerConnectionId = providerConnectionId;
        this.accessTokenEncrypted = accessTokenEncrypted;
        this.refreshTokenEncrypted = refreshTokenEncrypted;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.connectionStatus = IntegrationConnectionStatus.ACTIVE;
    }

    public void updateOAuthTokens(
            String accessTokenEncrypted,
            String refreshTokenEncrypted,
            Instant accessTokenExpiresAt
    ) {
        this.accessTokenEncrypted = accessTokenEncrypted;
        if (refreshTokenEncrypted != null && !refreshTokenEncrypted.isBlank()) {
            this.refreshTokenEncrypted = refreshTokenEncrypted;
        }
        this.accessTokenExpiresAt = accessTokenExpiresAt;
    }

    /** Provider 권한은 유지하고 Plog 내부 연결 자격증명만 제거한다. */
    public void disconnect() {
        this.connectionStatus = IntegrationConnectionStatus.REVOKED;
        clearOAuthCredentials();
    }

    public void requireReauthorization() {
        this.connectionStatus = IntegrationConnectionStatus.REAUTH_REQUIRED;
        clearOAuthCredentials();
    }

    private void clearOAuthCredentials() {
        this.accessTokenEncrypted = null;
        this.refreshTokenEncrypted = null;
        this.accessTokenExpiresAt = null;
    }
}
