package com.plog.domain.integration.entity;

import com.plog.domain.project.entity.Project;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 프로젝트가 추적하는 외부 리소스(repo / Figma file / Notion page).
// external_connection(계정 연결·토큰)과 추적 대상을 분리:
// - 한 프로젝트에 repo 여러 개 연결 가능
// - 리소스별 동기화 on/off 및 증분 동기화 북마크 관리
@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "external_resource", uniqueConstraints = {
        // 한 프로젝트에 같은 리소스 중복 등록 불가 → 서로 다른 connection으로
        // 같은 repo를 등록해 활동이 이중 집계되는 문제를 구조적으로 차단
        @UniqueConstraint(name = "uk_resource_project_type_external",
                columnNames = {"project_id", "resource_type", "external_resource_id"})
})
public class ExternalResource extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "resource_id")
    private Long id;

    // 이 리소스가 어느 프로젝트의 추적 대상인지 (소유)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // 동기화 시 어느 연결의 토큰을 쓸지 (자격증명).
    // 불변식: connection.projectMember.project == this.project → 스키마로 강제 불가, 등록 서비스에서 검증 필요
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false)
    private ExternalConnection connection;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    private ResourceType resourceType;

    // repo full name(owner/repo), Figma file key, Notion page/database id
    @Column(name = "external_resource_id", nullable = false)
    private String externalResourceId;

    // 표시용 캐시 (동기화 때 갱신)
    @Column(name = "resource_name")
    private String resourceName;

    @Column(name = "resource_url")
    private String resourceUrl;

    // 사용자 설정값(독립 사실)이므로 boolean 정당 — is_linked와 달리 유도 불가능
    @Builder.Default
    @Column(name = "sync_enabled", nullable = false)
    private boolean syncEnabled = true;

    // 리소스별 증분 동기화 북마크: 이 시각 이후의 활동만 외부 API에 요청
    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;
}
