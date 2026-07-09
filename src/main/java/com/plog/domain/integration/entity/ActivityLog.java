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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "activity_log", uniqueConstraints = {
        // 동기화 반복 실행 시 같은 활동(커밋 등)의 중복 적재 방지.
        // resource 단위로 걸어야 서로 다른 connection이 같은 repo를 동기화해도 한 줄만 적재됨
        @UniqueConstraint(name = "uk_activity_resource_external", columnNames = {"resource_id", "external_id"})
})
public class ActivityLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "activity_id")
    private Long id;

    // 이 활동이 발생한 외부 리소스 (repo / Figma file / Notion page).
    // connection은 resource를 경유해 유도 가능하므로 별도 FK를 두지 않음
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id", nullable = false)
    private ExternalResource resource;

    // NULL 허용: 외부 계정 매핑이 안 된 활동도 일단 적재 후,
    // 해당 멤버가 계정 연동을 완료하면 external_author 기준으로 backfill
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_member_id")
    private ProjectMember projectMember;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false)
    private ActivityType activityType;

    // 커밋 메시지, 페이지 제목, 버전 라벨 등
    @Column(name = "title")
    private String title;

    // 활동이 실제 발생한 시각 (동기화 시각 아님 → BaseEntity.createdAt과 다름)
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    // 커밋 SHA, 노션 페이지 ID+시각, 피그마 버전 ID 등 외부 고유 식별자
    @Column(name = "external_id", nullable = false)
    private String externalId;

    // 원본 API의 작성자 식별자 (깃허브 로그인, 노션 user id 등)
    @Column(name = "external_author", nullable = false)
    private String externalAuthor;

    // 변경 라인 수, 페이지 URL 등 소스별 상세
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
}
