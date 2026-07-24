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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "external_resource")
public class ExternalResource extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "resource_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false)
    private ExternalConnection connection;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    private ResourceType resourceType;

    @Column(name = "external_resource_id", nullable = false)
    private String externalResourceId;

    @Column(name = "resource_name")
    private String resourceName;

    @Column(name = "resource_url")
    private String resourceUrl;

    @Builder.Default
    @Column(name = "sync_enabled", nullable = false)
    private boolean syncEnabled = true;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    public void updateSyncCheckpoint(LocalDateTime syncedAt) {
        this.lastSyncedAt = syncedAt;
    }

    public void disableSync() {
        this.syncEnabled = false;
    }
}
